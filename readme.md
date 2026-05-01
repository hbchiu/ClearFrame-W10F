# ClearFrame (Original Notes)

ClearFrame is a custom photo slideshow application designed to run on my Nixplay W13D digital frame. I wrote it to replace the original software on the frame when Nixplay started charging subscription prices to load photos via the cloud. 

The application syncs and displays photos from a server hosted on my home Synology - I named it "ClearFrame" because it's not cloudy.

More info can be found in my write-ups about all the parts of the project:

[Part 1](https://ezhart.com/posts/digital-frame-hacking-1) - Opening the frame

[Part 2](https://ezhart.com/posts/digital-frame-hacking-2) - Setting the frame up for remote development

[Part 3](https://ezhart.com/posts/digital-frame-hacking-3) - Building a photo server

[Part 4](https://ezhart.com/posts/digital-frame-hacking-4) - This app (ClearFrame)

[Part 5](https://ezhart.com/posts/digital-frame-hacking-5) - Working with the remote control

[Part 6](https://ezhart.com/posts/digital-frame-hacking-6) - Working with the motion sensor

---

# Fork Notes: W10F and W08 Support

- Changed Synology to Immich as photo source.
- Added Config file support:
  - source = immich (may add alternate sources in future)
  - immich_url: enter your own immich url
  - immich_api_key: enter your own immich api key
  - immich album: choose one or more albums (if multiple, comma-separated)
  - display mode:
    - adaptive: standard-- photos rotate to fit frame and will fit frame
    - fill: all photos fill frame; sides will get cropped out when photo and frame orientation are mismatched
    - match: when frame is landscape, only landscape photos will be shown and will fill frame. when frame vertical, only portrait orientation photos will be shown and will fill frame
  - motion sensor: on/off (disclaimer: the motion sensors on my frame were too sensitive and triggered constantly, so this was functionally moot for me)
  - sleep and wake hour: select hours during which the frame will turn itself off


## Install Notes:

- Download and install apk
- Edit and push config file to frame
- Grant permissions:
  - adb shell pm grant com.ezhart.clearframe android.permission.READ_EXTERNAL_STORAGE
  - adb shell pm grant com.ezhart.clearframe android.permission.WRITE_EXTERNAL_STORAGE
  - adb shell cmd package set-home-activity com.ezhart.clearframe/.MainActivity
- Restart frame
