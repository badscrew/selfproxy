---
title: Mobile App Testing Protocol
inclusion: always
---

# Mobile App Testing Protocol

## Wait for User Testing After Deployment

When developing mobile applications (Android/iOS), always follow this protocol after deploying to a device:

### After Installing APK/IPA

1. **STOP and WAIT** - Do not immediately check logs or assume results
2. **Inform the user** - Tell them the app is installed and ready to test
3. **Provide clear testing instructions** - List specific actions they should take
4. **Wait for user feedback** - Let them actually test the app and report results
5. **Only then check logs** - After user reports their observations, check device logs

### Example Good Practice

```
✅ CORRECT:
"The app is installed. Please:
1. Open the app
2. Connect to your SSH server
3. Look for a VPN key icon in the status bar
4. Let me know what you see

I'll wait for your response before checking logs."
```

### Example Bad Practice

```
❌ WRONG:
"App installed. Let me check the logs..."
[Immediately runs: adb logcat -d | Select-String...]
[Checks logs from old sessions before user even tested]
```

## Why This Matters

1. **Old logs are misleading** - Device logs contain data from previous app sessions
2. **User needs time** - They need to physically interact with the device
3. **User observations are valuable** - What they see on screen is often more important than logs
4. **Respect user's pace** - They may need time to test thoroughly
5. **Avoid wasted work** - Checking old logs wastes time and creates confusion

## Testing Workflow

### Step 1: Deploy
```bash
adb install -r app-debug.apk
```

### Step 2: Inform User
"App installed successfully! Please test [specific feature] and let me know what happens."

### Step 3: WAIT
- Do NOT check logs yet
- Do NOT make assumptions
- Do NOT start debugging
- WAIT for user to report their observations

### Step 4: Get User Feedback
User reports: "I see X happening" or "Feature Y doesn't work"

### Step 5: Investigate
NOW you can:
- Check device logs
- Ask clarifying questions
- Propose fixes

## Special Cases

### VPN/Network Features
- User must grant permissions (VPN permission dialog)
- User must observe status bar icons
- User must check notifications
- These require physical device interaction - WAIT for them

### UI Changes
- User must see the visual changes
- Screenshots may be needed
- User feedback is primary source of truth

### Background Services
- May require user to navigate away from app
- May require time to trigger
- User needs to observe behavior over time

## Log Checking Protocol

### When to Check Logs

✅ **After user reports results**
✅ **When user describes unexpected behavior**
✅ **When user confirms they've tested**
✅ **When debugging a specific issue user reported**

❌ **Before user has tested**
❌ **Immediately after install**
❌ **While user is still testing**
❌ **Without asking user first**

### How to Check Logs

1. **Clear old logs first** (optional but recommended):
   ```bash
   adb logcat -c
   ```

2. **Ask user to perform action**:
   "Please click the Connect button now"

3. **Wait for user confirmation**:
   "Done" or "I clicked it"

4. **Then check logs**:
   ```bash
   adb logcat -d | Select-String -Pattern "YourTag"
   ```

## Summary

**Golden Rule**: After deploying a mobile app, ALWAYS wait for the user to test and report their observations before checking logs or making assumptions about what happened.

This ensures:
- Accurate information (not old log data)
- Respect for user's time and pace
- Better understanding of actual user experience
- More efficient debugging process
- Less confusion and wasted effort
