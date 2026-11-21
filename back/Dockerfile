FROM denoland/deno:2.1.4 AS development

WORKDIR /app

COPY deno.json deno.lock ./

COPY . .

RUN deno cache mod.ts

EXPOSE 1407

CMD ["deno", "task", "dev"]

FROM denoland/deno:2.1.4 AS production

WORKDIR /app

COPY deno.json deno.lock ./

COPY mod.ts ./
COPY src/ ./src/
COPY models/ ./models/
COPY utils/ ./utils/

RUN deno cache mod.ts

EXPOSE 1407

CMD ["deno", "run", "--allow-all", "mod.ts"]