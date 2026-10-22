import { cp, mkdir, rm, writeFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.dirname(fileURLToPath(import.meta.url));
const sourceDirectory = path.join(root, "src");
const outputDirectory = path.join(root, "dist");
const apiBaseUrl = process.env.LOCALLINK_API_BASE_URL?.trim() ?? "";

if (process.env.VERCEL && !apiBaseUrl) {
    throw new Error(
        "Set LOCALLINK_API_BASE_URL in Vercel to the Render API base URL."
    );
}

if (apiBaseUrl) {
    const apiUrl = new URL(apiBaseUrl);
    if (apiUrl.protocol !== "https:" && apiUrl.protocol !== "http:") {
        throw new Error("LOCALLINK_API_BASE_URL must use HTTP or HTTPS.");
    }
    if (process.env.VERCEL && apiUrl.protocol !== "https:") {
        throw new Error(
            "LOCALLINK_API_BASE_URL must use HTTPS for Vercel deployments."
        );
    }
}

await rm(outputDirectory, { recursive: true, force: true });
await cp(sourceDirectory, outputDirectory, { recursive: true });

const configDirectory = path.join(outputDirectory, "scripts");
await mkdir(configDirectory, { recursive: true });
await writeFile(
    path.join(configDirectory, "deployment-config.js"),
    `window.LocalLinkConfig = Object.freeze({\n`
        + `    apiBaseUrl: ${JSON.stringify(apiBaseUrl)}\n`
        + `});\n`,
    "utf8"
);

console.log(`Built static frontend in ${path.relative(root, outputDirectory)}.`);
