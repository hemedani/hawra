import { getMyReadingHistorySetup } from "./getMyReadingHistory/mod.ts";
import { syncReadingSessionsSetup } from "./syncReadingSessions/mod.ts";

export const sessionsSetup = () => {
  syncReadingSessionsSetup();
  getMyReadingHistorySetup();
};
