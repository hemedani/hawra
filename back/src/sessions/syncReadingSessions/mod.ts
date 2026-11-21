import { coreApp } from "../../../mod.ts";
import { syncReadingSessionsFn } from "./syncReadingSessions.fn.ts";
import { syncReadingSessionsValidator } from "./syncReadingSessions.val.ts";
import { setTokens, setUser } from "@lib";

export const syncReadingSessionsSetup = () =>
  coreApp.acts.setAct({
    schema: "readingSession",
    actName: "syncReadingSessions",
    validationRunType: "create",
    validator: syncReadingSessionsValidator(),
    preAct: [setTokens, setUser],
    fn: syncReadingSessionsFn,
  });
