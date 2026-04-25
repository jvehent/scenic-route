/** @type {import('jest').Config} */
module.exports = {
  preset: "ts-jest",
  testEnvironment: "node",
  testMatch: ["**/__tests__/**/*.test.ts"],
  moduleFileExtensions: ["ts", "js", "json"],
  // Don't try to compile or run anything inside lib/ (build output) or node_modules.
  testPathIgnorePatterns: ["/node_modules/", "/lib/"],
};
