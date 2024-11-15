## RicohSync

A simple app that syncs the GPS data and date/time from your Android phone to your Ricoh camera.

This app will let you pick your Camera from visible nearby BLE devices on first start. When the
camera is turned on and/or its Bluetooth connection is turned on, the app will just reconnect
automatically and start syncing, unless you close it.

Note that the app MUST be in the foreground while it's connecting to the camera, but it can be left
in the background after that. You may or may not need/want to exclude it from battery optimisations
and permissions removal.

> [!IMPORTANT]
> I have only tested this on my Pixel 9 running Android 15 and my GR IIIx. Any other configuration
> _might_ work, but I haven't tested it and make no guarantees.

### License

Copyright 2024 Sebastiano Poggi

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
