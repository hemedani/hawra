import { lesan, MongoClient } from "lesan";
import {
  audioFiles,
  mediaEditions,
  presetAyahProgresses,
  presets,
  presetSessions,
  readingSessions,
  recitationEditions,
  reciters,
  users,
} from "@model";
import { functionsSetup } from "./src/mod.ts";
import { ensureIndexes } from "./utils/ensureIndexes.ts";
import { printLanBanner } from "./utils/networkBanner.ts";

const MONGO_URI = Deno.env.get("MONGO_URI") || "mongodb://127.0.0.1:27017/";
const DB_NAME = Deno.env.get("DB_NAME") || "hawraa";
const APP_PORT = Deno.env.get("APP_PORT") || 1407;
const ENV = Deno.env.get("ENV") || "development";

export const coreApp = lesan();
const client = await new MongoClient(MONGO_URI, { ignoreUndefined: true })
  .connect();
const db = client.db(DB_NAME);
coreApp.odm.setDb(db);

// Every model is instantiated here, once, in dependency order: an act or a model file
// imports the instance from this module rather than calling a factory itself.
export const user = users();
export const readingSession = readingSessions();
export const reciter = reciters();
export const recitationEdition = recitationEditions();
/** General media tied to verses — guya audio now, video/images later. See `models/mediaEdition.ts`. */
export const mediaEdition = mediaEditions();
/** The server's own copies of recitation files — see `src/recitationAudio/`. */
export const audioFile = audioFiles();
export const preset = presets();
export const presetAyahProgress = presetAyahProgresses();
export const presetSession = presetSessions();

// Compound indexes Lesan's single `createIndex` option cannot express (see utils/ensureIndexes.ts).
await ensureIndexes(db);

export const { setAct } = coreApp.acts;
export const { selectStruct, getSchemas } = coreApp.schemas;

functionsSetup();

if (import.meta.main) {
  printLanBanner(Number(APP_PORT));
  coreApp.runServer({
    port: Number(APP_PORT),
    typeGeneration: true,
    playground: ENV === "development",
    cors: ["http://localhost:3000", "http://localhost:8081"],
    // `/uploads` is where the audio mirror writes (`src/recitationAudio/paths.ts`). The
    // mirror and this list must agree: the handler serves `${cwd}${url.pathname}`, so a
    // file at `uploads/recitation/x.mp3` is reachable at `/uploads/recitation/x.mp3`.
    staticPath: ["/uploads"],
  });
}
