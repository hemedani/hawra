import { coreApp } from "../../../mod.ts";
import { getMyReadingHistoryFn } from "./getMyReadingHistory.fn.ts";
import { getMyReadingHistoryValidator } from "./getMyReadingHistory.val.ts";
import { setTokens, setUser } from "@lib";

export const getMyReadingHistorySetup = () =>
  coreApp.acts.setAct({
    schema: "readingSession",
    actName: "getMyReadingHistory",
    validator: getMyReadingHistoryValidator(),
    preAct: [setTokens, setUser],
    fn: getMyReadingHistoryFn,
  });
