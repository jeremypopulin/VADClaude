Kiosk Mode Testing Instructions
Overview
This application supports two operating modes:
1. Normal Mode - works like a standard Android app
2. Kiosk Mode (Device Owner) - fully locked, enterprise-grade mode with no escape
To test full Kiosk Mode, the device must be configured correctly.
Important Requirements
- Android device must be factory reset
- USB debugging must be enabled
- Testing must be done on a physical device (emulators do NOT support full kiosk)
- This process is one-time only per device
Step 1: Factory Reset the Device
1. Open Settings
2. Go to System -> Reset options
3. Select Erase all data (factory reset)
4. Complete initial Android setup (do NOT sign in to Google if possible)
Step 2: Enable Developer Options
1. Open Settings -> About phone
2. Tap Build number 7 times
3. Go back -> Developer options
4. Enable USB debugging
Step 3: Install the App
1. Connect the phone to a computer via USB
2. Install the application APK using Android Studio or ADB
Step 4: Enable Kiosk Mode (Device Owner)
Run the following command once from the computer:
adb shell dpm set-device-owner com.example.visualduress/.KioskAdminReceiver
Expected result:
- Command returns success
- App automatically becomes Device Owner
- Kiosk mode activates
If this command fails: device was not factory reset
Step 5: Test Kiosk Behavior
Navigation Restrictions
- Home button -> Blocked
- Back button -> Blocked
- Recent apps -> Blocked
- Status bar pull-down -> Disabled
System Access
- Settings app -> Not accessible
- Other apps -> Not accessible
- App uninstall -> Not possible
Persistence
- Restart the device: App should auto-launch, Kiosk mode remains active
Normal Mode Behavior (Without Device Owner)
- App runs normally
- Navigation buttons work
- No restrictions applied
- No crash or errors
Security Notes
- Kiosk Mode uses Android Device Owner APIs
- Sensitive data is protected using Android Keystore (AES-GCM)
- No passwords or secrets are stored in plain text
- Security is device-bound and offline
How to Exit Kiosk Mode
- Exit is restricted to authorized administrators only
- End users cannot escape kiosk mode
- Device reset is required to remove Device Owner
Summary
- Normal install -> behaves like a standard app
- Device Owner enabled -> full enterprise kiosk
- No backend or internet required
- Designed for secure, offline environments


hi i am naeem qadir 
please share your contact infromation on here 
i want to be direct for next time
we are not allowed on fiver to share details that why i was not enabled to be direct
my contact number is 
please dont discuss this thing on fivver
whats app : +923126519326 
email : naeem.x56@gmail.com
