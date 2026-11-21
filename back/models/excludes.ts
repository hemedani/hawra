export const user_excludes: string[] = [
  "createdAt",
  "updatedAt",
];

export const session_excludes: string[] = [
  "createdAt",
  "updatedAt",
  "deviceInfo",
];

/** Embedded on a session; the bookkeeping dates belong to the session row, not the copy. */
export const preset_excludes: string[] = [
  "createdAt",
  "updatedAt",
];

/** Embedded on a preset (the edition it names) — keep the descriptive fields, drop bookkeeping. */
export const edition_excludes: string[] = [
  "createdAt",
  "updatedAt",
];
