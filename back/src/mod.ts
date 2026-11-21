import { presetProgressSetup } from "./presetProgress/mod.ts";
import { presetSessionsSetup } from "./presetSessions/mod.ts";
import { presetsSetup } from "./presets/mod.ts";
import { mediaEditionsSetup } from "./mediaEditions/mod.ts";
import { recitationAudioSetup } from "./recitationAudio/mod.ts";
import { recitationEditionsSetup } from "./recitationEditions/mod.ts";
import { recitersSetup } from "./reciters/mod.ts";
import { sessionsSetup } from "./sessions/mod.ts";
import { usersSetup } from "./users/mod.ts";

export const functionsSetup = () => {
  usersSetup();
  sessionsSetup();
  // Catalogue (read-only reference data)
  recitersSetup();
  recitationEditionsSetup();
  // General verse-tied media: guya audio now, video/images later
  mediaEditionsSetup();
  // The server's own copies of that catalogue's audio
  recitationAudioSetup();
  // User-owned recitation patterns + their activity
  presetsSetup();
  presetSessionsSetup();
  presetProgressSetup();
};
