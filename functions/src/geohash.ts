const BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz";

export const INDEX_PRECISION = 5;

export function encodeGeohash(lat: number, lng: number, precision = 9): string {
  let latLo = -90,
    latHi = 90;
  let lngLo = -180,
    lngHi = 180;
  let out = "";
  let bit = 0;
  let ch = 0;
  let even = true;
  while (out.length < precision) {
    if (even) {
      const mid = (lngLo + lngHi) / 2;
      if (lng >= mid) {
        ch |= 1 << (4 - bit);
        lngLo = mid;
      } else {
        lngHi = mid;
      }
    } else {
      const mid = (latLo + latHi) / 2;
      if (lat >= mid) {
        ch |= 1 << (4 - bit);
        latLo = mid;
      } else {
        latHi = mid;
      }
    }
    even = !even;
    bit++;
    if (bit === 5) {
      out += BASE32[ch];
      bit = 0;
      ch = 0;
    }
  }
  return out;
}

export function indexPrefix(geohash: string | null | undefined): string | null {
  if (!geohash || geohash.length < INDEX_PRECISION) return null;
  return geohash.substring(0, INDEX_PRECISION);
}
