# Website to Android APK Converter (Serverless Architecture)
**Blogger Frontend + Cloudflare Worker Proxy + GitHub Actions APK Compiler**

Is ZIP archive mein aapke "Website to APK Converter" web app ka complete production-ready code 3 folders mein divided hai:

---

## Folder Structure

1. **`1-blogger-frontend/blogger-apk-converter.html`**
   - Single-file Cyberpunk/Neon UI tool jo seedha Blogger ke Page ya Post ke **HTML View** mein paste hoga.
2. **`2-cloudflare-worker/`**
   - `worker.js`: Cloudflare Worker security proxy jo aapke GitHub Personal Access Token (`GITHUB_PAT`) ko chhupa kar rakhta hai aur `/build` + `/status` API endpoints deta hai.
   - `wrangler.toml`: Optional Wrangler CLI config file.
3. **`3-github-android-repo/`**
   - Pura Android WebView project (`MainActivity.java`, `AndroidManifest.xml`, `build.gradle`, `strings.xml`) aur GitHub Actions workflow (`.github/workflows/build-apk.yml`).

---

## Step-by-Step Setup Guide (100% Free)

### STEP 1: GitHub Repository Setup
1. Apne GitHub account par ek nayi **Public Repository** banayein (jaise: `web2apk-builder`) aur uska default branch `main` rakhein.
2. `3-github-android-repo/` folder ke andar ki saari files aur folders (including `.github/workflows/build-apk.yml`) ko apni GitHub repository ke root mein upload/push kar dein.
3. Repository ke **Settings > Actions > General > Workflow permissions** mein jaakar **"Read and write permissions"** select karke Save karein (taaki workflow GitHub Releases mein `.apk` upload kar sake).

### STEP 2: Free GitHub Personal Access Token (`GITHUB_PAT`) Banayein
1. GitHub ke **Settings > Developer settings > Personal access tokens > Fine-grained tokens** par jayein.
2. **Generate new token** par click karein, apni `web2apk-builder` repository select karein.
3. **Repository permissions** mein:
   - **Actions**: `Read and write`
   - **Contents**: `Read and write`
4. Token generate karke copy kar lein.

### STEP 3: Cloudflare Worker Deploy Karein
1. [Cloudflare Dashboard](https://dash.cloudflare.com/) mein **Workers & Pages** par jayein aur ek naya Worker create karein (jaise `apk-builder-proxy`).
2. `2-cloudflare-worker/worker.js` ka pura code Worker ke andar paste karke **Deploy** kar dein.
3. Ab Worker ke **Settings > Variables and Secrets** mein jaakar ye 4 variables add karein:
   - `GITHUB_PAT`: Aapka GitHub Token (Secret encrypt karein)
   - `GITHUB_OWNER`: Aapka GitHub username
   - `GITHUB_REPO`: Aapki repository ka naam (jaise `web2apk-builder`)
   - `ALLOWED_ORIGIN`: `*` (ya aapka Blogger domain URL, jaise `https://yourblog.blogspot.com`)
4. Apna Worker URL copy kar lein (jaise `https://apk-builder-proxy.yourname.workers.dev`).

### STEP 4: Blogger Par Frontend Lagayein
1. `1-blogger-frontend/blogger-apk-converter.html` ko kisi bhi text editor mein kholein.
2. Line 490 ke aas-paas `WORKER_BASE_URL` mein apna Cloudflare Worker URL daalein:
   ```javascript
   const WORKER_BASE_URL = "https://apk-builder-proxy.yourname.workers.dev";
   ```
3. Ab ye pura HTML code copy karke Blogger ke **Page** ya **Post** ke **HTML View** mein paste karein aur **Publish** kar dein!
