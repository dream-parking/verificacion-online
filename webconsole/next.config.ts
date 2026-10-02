import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // Required by Azure Static Web Apps hybrid Next.js (keeps the bundle under the 250 MB Free limit).
  output: "standalone",
};

export default nextConfig;
