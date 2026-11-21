import {
  assertEquals,
  assertStringIncludes,
  assertThrows,
} from "jsr:@std/assert@^1";
import { looksLikeMp3 } from "./mp3.ts";
import { resolveUnderRoot } from "./paths.ts";
import {
  type MirrorFileInput,
  mirrorFiles,
  type MirrorStore,
  type StoredAudio,
} from "./mirror.ts";

/**
 * These tests run the *real* resume / verify / rename code against a real local HTTP server
 * and a real temp directory. The database is the only thing stubbed, because the database is
 * the only thing that cannot be wrong in an interesting way here.
 *
 * The failure this file exists to prevent: a download that reports success and produces a
 * file that is the wrong length. Every case below is a different way that could happen.
 */

const FRAME_SYNC = new Uint8Array([0xff, 0xfb, 0x90, 0x64]);

/**
 * `Uint8Array<ArrayBufferLike>` is not assignable to `BodyInit` in the DOM lib, because the
 * view could be backed by a SharedArrayBuffer. Every buffer here is a plain allocation, so
 * the cast is sound — and it is confined to this one helper rather than sprinkled around.
 */
const bodyOf = (bytes: Uint8Array): BodyInit => bytes as unknown as BodyInit;

const fakeAudio = (bytes: number): Uint8Array => {
  const body = new Uint8Array(bytes);
  body.set(FRAME_SYNC, 0);
  for (let i = FRAME_SYNC.length; i < bytes; i++) body[i] = i % 251;
  return body;
};

interface TestServer {
  url: string;
  requests: { range: string | null; path: string }[];
  /** `honour` answers 206; `ignore` answers 200 with the whole body, as some CDNs do. */
  mode: "honour" | "ignore";
  body: Uint8Array;
  shutdown: () => Promise<void>;
}

const startServer = (
  body: Uint8Array,
  mode: TestServer["mode"],
): TestServer => {
  const state: TestServer = {
    url: "",
    requests: [],
    mode,
    body,
    shutdown: async () => {},
  };

  const server = Deno.serve(
    { port: 0, hostname: "127.0.0.1", onListen: () => {} },
    (request) => {
      const range = request.headers.get("range");
      state.requests.push({ range, path: new URL(request.url).pathname });

      if (range && state.mode === "honour") {
        const start = Number(range.replace(/bytes=/, "").replace(/-.*$/, ""));
        const slice = state.body.subarray(start);
        return new Response(bodyOf(slice), {
          status: 206,
          headers: {
            "content-type": "audio/mpeg",
            "content-range": `bytes ${start}-${
              state.body.length - 1
            }/${state.body.length}`,
          },
        });
      }
      return new Response(bodyOf(state.body), {
        status: 200,
        headers: { "content-type": "audio/mpeg" },
      });
    },
  );

  state.url = `http://127.0.0.1:${server.addr.port}`;
  state.shutdown = () => server.shutdown();
  return state;
};

/** In-memory [MirrorStore]: enough to prove the engine writes the right rows. */
const memoryStore = () => {
  const rows = new Map<string, StoredAudio>();
  const failures: { fileKey: string; error: string }[] = [];
  const store: MirrorStore = {
    existing: (_edition, fileKey) => Promise.resolve(rows.get(fileKey) ?? null),
    recordStored: (_edition, file, result) => {
      rows.set(file.fileKey, result);
      return Promise.resolve();
    },
    recordFailure: (_edition, file, error) => {
      failures.push({ fileKey: file.fileKey, error });
      return Promise.resolve();
    },
  };
  return { store, rows, failures };
};

const file = (overrides: Partial<MirrorFileInput> = {}): MirrorFileInput => ({
  fileKey: "ayah:1:1",
  kind: "ayah",
  surah: 1,
  ayah: 1,
  globalAyah: 1,
  relativePath: "test-edition/ayah/001/001.mp3",
  url: "http://127.0.0.1:1/none",
  ...overrides,
});

const withTempRoot = async (
  run: (root: string) => Promise<void>,
): Promise<void> => {
  const root = await Deno.makeTempDir({ prefix: "hawraa-mirror-" });
  try {
    await run(root);
  } finally {
    await Deno.remove(root, { recursive: true });
  }
};

// ---- the frame check ---------------------------------------------------------------

Deno.test("mp3: an ID3 tag counts as audio", () => {
  assertEquals(
    looksLikeMp3(new Uint8Array([0x49, 0x44, 0x33, 0x04, 0x00])),
    true,
  );
});

Deno.test("mp3: a bare MPEG frame sync counts as audio", () => {
  assertEquals(looksLikeMp3(fakeAudio(64)), true);
});

Deno.test("mp3: an HTML error page does not", () => {
  const html = new TextEncoder().encode(
    "<html><body>403 Forbidden</body></html>",
  );
  assertEquals(looksLikeMp3(html), false);
});

Deno.test("mp3: reserved version/layer bits are not a frame", () => {
  // 0xFF 0xF9 → version bits 0b11 (MPEG1), layer bits 0b00 (reserved).
  assertEquals(looksLikeMp3(new Uint8Array([0xff, 0xf9, 0x00, 0x00])), false);
});

Deno.test("mp3: an empty buffer is not audio", () => {
  assertEquals(looksLikeMp3(new Uint8Array()), false);
});

// ---- path containment --------------------------------------------------------------

Deno.test("paths: a normal relative path resolves under the root", () => {
  assertEquals(
    resolveUnderRoot("/tmp/audio", "ed/ayah/001/001.mp3"),
    "/tmp/audio/ed/ayah/001/001.mp3",
  );
});

Deno.test("paths: traversal is refused", () => {
  assertThrows(() => resolveUnderRoot("/tmp/audio", "../etc/passwd"));
});

Deno.test("paths: an absolute path is refused", () => {
  assertThrows(() => resolveUnderRoot("/tmp/audio", "/etc/passwd"));
});

// ---- the engine --------------------------------------------------------------------

Deno.test("mirror: stores the file, records its real size, and reports it", async () => {
  await withTempRoot(async (root) => {
    const body = fakeAudio(20_000);
    const server = startServer(body, "honour");
    const { store, rows } = memoryStore();
    try {
      const summary = await mirrorFiles(
        [file({ url: `${server.url}/1.mp3` })],
        store,
        { editionKey: "test-edition", root },
      );

      assertEquals(summary.stored, 1);
      assertEquals(summary.failed, 0);
      assertEquals(summary.bytes, body.length);

      const written = await Deno.readFile(
        `${root}/test-edition/ayah/001/001.mp3`,
      );
      assertEquals(written.length, body.length);
      assertEquals(written[0], 0xff);
      assertEquals(rows.get("ayah:1:1")?.bytes, body.length);
      assertEquals(rows.get("ayah:1:1")?.sha256?.length, 64);
    } finally {
      await server.shutdown();
    }
  });
});

Deno.test("mirror: a second run skips an intact file without a request", async () => {
  await withTempRoot(async (root) => {
    const server = startServer(fakeAudio(4_000), "honour");
    const { store } = memoryStore();
    try {
      const files = [file({ url: `${server.url}/1.mp3` })];
      await mirrorFiles(files, store, { editionKey: "test-edition", root });
      const afterFirst = server.requests.length;

      const second = await mirrorFiles(files, store, {
        editionKey: "test-edition",
        root,
      });

      assertEquals(second.skipped, 1);
      assertEquals(second.stored, 0);
      assertEquals(server.requests.length, afterFirst);
    } finally {
      await server.shutdown();
    }
  });
});

Deno.test("mirror: force re-fetches an intact file", async () => {
  await withTempRoot(async (root) => {
    const server = startServer(fakeAudio(4_000), "honour");
    const { store } = memoryStore();
    try {
      const files = [file({ url: `${server.url}/1.mp3` })];
      await mirrorFiles(files, store, { editionKey: "test-edition", root });
      const second = await mirrorFiles(files, store, {
        editionKey: "test-edition",
        root,
        force: true,
      });
      assertEquals(second.stored, 1);
      assertEquals(server.requests.length, 2);
    } finally {
      await server.shutdown();
    }
  });
});

Deno.test("mirror: resumes a .part with a Range request and lands a complete file", async () => {
  await withTempRoot(async (root) => {
    const body = fakeAudio(30_000);
    const server = startServer(body, "honour");
    const { store } = memoryStore();
    const target = `${root}/test-edition/ayah/001/001.mp3`;
    try {
      await Deno.mkdir(`${root}/test-edition/ayah/001`, { recursive: true });
      // Half a file from an earlier interrupted run.
      await Deno.writeFile(`${target}.part`, body.subarray(0, 12_000));

      const summary = await mirrorFiles(
        [file({ url: `${server.url}/1.mp3` })],
        store,
        { editionKey: "test-edition", root },
      );

      assertEquals(summary.stored, 1);
      assertEquals(server.requests[0].range, "bytes=12000-");
      const written = await Deno.readFile(target);
      assertEquals(written.length, body.length);
      // The join has to be byte-exact, not just the right length.
      assertEquals(written[12_000], body[12_000]);
      assertEquals(written[written.length - 1], body[body.length - 1]);
    } finally {
      await server.shutdown();
    }
  });
});

Deno.test("mirror: a server that ignores the range does not produce a doubled file", async () => {
  await withTempRoot(async (root) => {
    const body = fakeAudio(10_000);
    const server = startServer(body, "ignore");
    const { store } = memoryStore();
    const target = `${root}/test-edition/ayah/001/001.mp3`;
    try {
      await Deno.mkdir(`${root}/test-edition/ayah/001`, { recursive: true });
      await Deno.writeFile(`${target}.part`, body.subarray(0, 5_000));

      const summary = await mirrorFiles(
        [file({ url: `${server.url}/1.mp3` })],
        store,
        { editionKey: "test-edition", root },
      );

      assertEquals(summary.stored, 1);
      // 10 000, not 15 000: the partial was discarded because the answer was 200.
      assertEquals((await Deno.readFile(target)).length, body.length);
    } finally {
      await server.shutdown();
    }
  });
});

Deno.test("mirror: an HTML body fails and leaves no file behind", async () => {
  await withTempRoot(async (root) => {
    const html = new TextEncoder().encode("<html>403</html>");
    const server = startServer(html, "honour");
    const { store, rows, failures } = memoryStore();
    try {
      const summary = await mirrorFiles(
        [file({ url: `${server.url}/1.mp3` })],
        store,
        { editionKey: "test-edition", root },
      );

      assertEquals(summary.failed, 1);
      assertEquals(summary.stored, 0);
      assertEquals(failures.length, 1);
      assertStringIncludes(failures[0].error, "not audio");
      assertEquals(rows.size, 0);

      // Neither the final name nor a poison `.part` may survive.
      let exists = true;
      try {
        await Deno.stat(`${root}/test-edition/ayah/001/001.mp3`);
      } catch {
        exists = false;
      }
      assertEquals(exists, false);
      let partExists = true;
      try {
        await Deno.stat(`${root}/test-edition/ayah/001/001.mp3.part`);
      } catch {
        partExists = false;
      }
      assertEquals(partExists, false);
    } finally {
      await server.shutdown();
    }
  });
});

Deno.test("mirror: a 404 is recorded as a failure, not thrown", async () => {
  await withTempRoot(async (root) => {
    const server = Deno.serve(
      { port: 0, hostname: "127.0.0.1", onListen: () => {} },
      () => new Response("nope", { status: 404 }),
    );
    const { store, failures } = memoryStore();
    try {
      const summary = await mirrorFiles(
        [file({ url: `http://127.0.0.1:${server.addr.port}/1.mp3` })],
        store,
        { editionKey: "test-edition", root },
      );
      assertEquals(summary.failed, 1);
      assertStringIncludes(failures[0].error, "HTTP 404");
    } finally {
      await server.shutdown();
    }
  });
});

Deno.test("mirror: limit caps the work", async () => {
  await withTempRoot(async (root) => {
    const server = startServer(fakeAudio(2_000), "honour");
    const { store } = memoryStore();
    try {
      const files = [1, 2, 3].map((n) =>
        file({
          fileKey: `ayah:1:${n}`,
          relativePath: `test-edition/ayah/001/00${n}.mp3`,
          url: `${server.url}/${n}.mp3`,
        })
      );
      const summary = await mirrorFiles(files, store, {
        editionKey: "test-edition",
        root,
        limit: 2,
      });
      assertEquals(summary.total, 2);
      assertEquals(summary.stored, 2);
    } finally {
      await server.shutdown();
    }
  });
});

Deno.test("mirror: dry run touches neither the network nor the disk", async () => {
  await withTempRoot(async (root) => {
    const server = startServer(fakeAudio(1_000), "honour");
    const { store, rows } = memoryStore();
    try {
      const summary = await mirrorFiles(
        [file({ url: `${server.url}/1.mp3` })],
        store,
        { editionKey: "test-edition", root, dryRun: true },
      );
      assertEquals(summary.stored, 0);
      assertEquals(summary.skipped, 0);
      assertEquals(server.requests.length, 0);
      assertEquals(rows.size, 0);
    } finally {
      await server.shutdown();
    }
  });
});
