import type { NextConfig } from "next";
import { assertApiUrl } from "./src/lib/console/buildEnv";

// A Dev or QA build without the API URL must fail here, not call another environment (VDI-140).
assertApiUrl(process.env);

const nextConfig: NextConfig = {
  // Required by Azure Static Web Apps hybrid Next.js (keeps the bundle under the 250 MB Free limit).
  output: "standalone",
};

export default nextConfig;
