# Student app notifications (Firebase)

The student app uses the same Firebase project as the teacher app (`engquest-15b64`), as a second
Android app: package `com.engquest.app`, nickname "EngQuest Student" (added 2026-11-11).

`app/google-services.json` is git-ignored. To recreate it: Firebase console → Project settings →
General → Your apps → EngQuest Student → download google-services.json → save it in
`native-student/app/`. Without it the app still builds and runs, just without notifications.

The server sends the notifications (Worker secret `FCM_SERVICE_ACCOUNT`, shared with the teacher
app); students' phones and their switches are stored in the `student_devices` table.
