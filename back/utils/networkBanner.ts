/**
 * Lists the URLs this server is reachable at from *other devices* on the network.
 *
 * The banner Lesan prints says `localhost`, which is correct and useless the moment you want
 * to open the app on a phone. Every development session that involves a physical device
 * otherwise starts with "what is my IP again?" — and the failure mode when you guess wrong is
 * silence on the phone, not an error.
 *
 * Only **private IPv4** addresses are listed, which is a stricter rule than "not loopback"
 * and worth the four extra lines: a laptop with a VPN is routinely carrying addresses like
 * `240.0.0.2` on `utun3`, and printing that next to the real one turns a helpful banner into
 * a 50/50 guess. A phone on the same Wi-Fi can only reach a private address anyway.
 */
const isPrivateIPv4 = (address: string): boolean => {
  const octets = address.split(".").map(Number);
  if (octets.length !== 4 || octets.some((n) => !Number.isInteger(n))) {
    return false;
  }
  const [a, b] = octets;
  if (a === 10) return true;
  if (a === 172 && b >= 16 && b <= 31) return true;
  if (a === 192 && b === 168) return true;
  return false;
};

export const lanUrls = (port: number): string[] => {
  const urls: string[] = [];
  for (const iface of Deno.networkInterfaces()) {
    if (iface.family !== "IPv4") continue;
    if (!isPrivateIPv4(iface.address)) continue;
    urls.push(`http://${iface.address}:${port}/lesan   (${iface.name})`);
  }
  return urls;
};

export const printLanBanner = (port: number): void => {
  const urls = lanUrls(port);
  if (urls.length === 0) {
    console.log("📶 No private LAN address found — is this machine on Wi-Fi?");
    console.log("");
    return;
  }
  console.log("📶 Reachable from your phone (same Wi-Fi):");
  for (const url of urls) console.log(`   ${url}`);
  console.log(
    "   → set `hawraa.baseUrl` in mobile/android/local.properties to that host, without /lesan",
  );
  console.log("");
};
