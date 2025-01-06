# presentation_displays

#### Supported mobile platforms iOS and Android

Flutter plugin supports to run on two screens. It's basically a tablet connected to another screen via an HDMI or Wireless

 add in pubspec.yaml
 ```yaml
  presentation_displays: 
    git:
      url: https://github.com/wowplayah/presentation-displays.git
      ref: master
```


Idea: We create a `Widget` by using Flutter code and pass it to Native code side then convert it to` FlutterEngine` and save it to `FlutterEngineCache` for later use.

Next, we define the Display by using displayId and we will define the UI flutter that needs to display by grabbing `FlutterEngine` in `FlutterEngineCache` and transferring it to Dialog `Presentation` as a View.

We provide methods to get a list of connected devices and the information of each device then transfer data from the main display to the secondary display.

Simple steps:

- Create Widgets that need to display and define them as a permanent router when you configure the router in the Flutter code.

- Get the Displays list by calling `displayManager.getDisplays ()`

- Define which Display needs to display
For instance: `displays [1] .displayId` Display the index 2.

- Display it on Display with your routerName as `presentation` `displayManager.showSecondaryDisplay (displayId: displays [1] .displayId, routerName: "presentation") `

- Transmit the data from the main display to the secondary display by `displayManager.transferDataToPresentation (" test transfer data ")`
- The secondary screen receives data

```dart
@override
Widget build (BuildContext context) {
    return SecondaryDisplay (
        callback: (argument) {
        setState (() {
        value = argument;
        });
    }
    );
}
```
- wesetup new entry point for secondary display so we can decided what we need to call for initialization. Works only for android for now
```dart
@pragma('vm:entry-point')
void secondaryDisplayMain() {
 /// do something that don't break plugin registration here.
}
```
### Upgrade version 1.0.0

- Able to package android release build. Works fine in example app.

- Tested example app in android tab and ios tab and things work as expected. Ensure the devices have USB C 3.0 and above else HDMI out is not supported.

- In case of iOS, please refer to example app app delegate. There are few lines of code which needs to be added to your app's app delegate as well for this to work fine in iOS.

- Updated optional issues and null checks

- Added option to hide second display from the first

- WIP support second main in iOS for extended display

- WIP Send data back from 2nd to 1st display

You can take a look at our example to learn more about how the plugin works

#### Test on Sunmi-D2 device

![The example app running in android](https://github.com/VNAPNIC/presentation-displays/blob/master/Sequence_small.gif?raw=true)

