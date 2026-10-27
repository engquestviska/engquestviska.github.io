# Turning on submission notifications (Firebase)

Notifications use Google's free Firebase service. This is a one-time setup that takes
about 5 minutes. You only need your Google account.

## 1. Create the project

1. Open https://console.firebase.google.com and sign in with your Google account.
2. Click **Create a project** (or **Add project**).
3. Name it **EngQuest**, then click **Continue**.
4. When asked about Google Analytics, switch it **off**, then click **Create project**.
5. When it says the project is ready, click **Continue**.

## 2. Add the teacher app

1. On the project page, click the **Android** icon ("Add app").
2. Android package name: type exactly `com.engquest.teacher`
3. App nickname: `EngQuest Teacher`. Leave the other box empty.
4. Click **Register app**.
5. Click **Download google-services.json**. Save it in this folder:
   `C:\Users\acer\Documents\Github Engquest\native-teacher\app\`
6. Click **Next** / **Continue to console** on the remaining screens. You don't need
   to change anything else.

## 3. Create the server key

1. Click the gear icon next to "Project Overview", then **Project settings**.
2. Open the **Service accounts** tab.
3. Click **Generate new private key**, then **Generate key**. A `.json` file downloads.
4. Keep this file private (it lets the server send notifications). Leave it in your
   Downloads folder and tell Claude its name. Claude stores it as a server secret
   (`npx wrangler secret put FCM_SERVICE_ACCOUNT`) without showing its contents, and you
   can then delete the downloaded file.

## That's it

Tell Claude "Firebase done". The app gets rebuilt with notifications turned on.
`google-services.json` is ignored by git, so it never ends up on GitHub.
