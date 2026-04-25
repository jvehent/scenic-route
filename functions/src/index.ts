import * as admin from "firebase-admin";

admin.initializeApp();

export { onDriveWritten } from "./driveIndex";
export { onDriveDeleted } from "./driveCascade";
export { onProfileWritten } from "./profileScrub";
export { cleanupTrashedDrives } from "./scheduledCleanup";
export { onUserDocCreated } from "./profileBootstrap";
export { onCommentCreated } from "./commentRateLimit";
