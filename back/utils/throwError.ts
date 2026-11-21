/**
 * Throws a developer-facing error.
 *
 * Declared as a function with an explicit `never` return type on purpose: TypeScript only
 * narrows control flow through a never-returning call when the callee is a function
 * declaration (or a const with an explicit type annotation). Written as an arrow assigned to
 * a const, `if (!row) throwError(...)` left `row` nullable at every call site — which cost a
 * round of `as` casts in every act.
 */
export function throwError(msg: string): never {
  throw new Error(msg);
}
