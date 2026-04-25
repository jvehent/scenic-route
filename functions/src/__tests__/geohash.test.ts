import { encodeGeohash, indexPrefix, INDEX_PRECISION } from "../geohash";

describe("encodeGeohash", () => {
  test("returns the requested precision", () => {
    expect(encodeGeohash(40, -73, 5)).toHaveLength(5);
    expect(encodeGeohash(40, -73, 9)).toHaveLength(9);
    expect(encodeGeohash(0, 0, 1)).toHaveLength(1);
  });

  test("known value: Paris at precision 5 → u09tv", () => {
    expect(encodeGeohash(48.8566, 2.3522, 5)).toBe("u09tv");
  });

  test("known value: NYC at precision 5 → dr5re", () => {
    expect(encodeGeohash(40.7128, -74.006, 5)).toBe("dr5re");
  });

  test("known value: Sydney at precision 5 → r3gx2", () => {
    expect(encodeGeohash(-33.8688, 151.2093, 5)).toBe("r3gx2");
  });

  test("nearby points share a prefix", () => {
    const a = encodeGeohash(40.7128, -74.006, 9);
    const b = encodeGeohash(40.713, -74.0061, 9);
    expect(a.slice(0, 4)).toBe(b.slice(0, 4));
  });

  test("uses only the standard base32 alphabet", () => {
    const allowed = new Set("0123456789bcdefghjkmnpqrstuvwxyz".split(""));
    const g = encodeGeohash(12.3, 45.6, 9);
    for (const ch of g) {
      expect(allowed.has(ch)).toBe(true);
    }
  });
});

describe("indexPrefix", () => {
  test("returns the first INDEX_PRECISION chars", () => {
    expect(indexPrefix("u09tvm0c0")).toBe("u09tv");
    expect(indexPrefix("dr5re12cs")).toBe("dr5re");
    expect(INDEX_PRECISION).toBe(5);
  });

  test("returns null for shorter or missing geohashes", () => {
    expect(indexPrefix(null)).toBeNull();
    expect(indexPrefix(undefined)).toBeNull();
    expect(indexPrefix("dr5")).toBeNull();
    expect(indexPrefix("")).toBeNull();
  });
});
