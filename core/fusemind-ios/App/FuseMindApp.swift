//
//  FuseMindApp.swift
//  FuseMind (iOS app target)
//
//  Minimal Phase-1 test harness host. Its only job for P1-002 is to put the
//  BLEWatchAdapter on a real device so scan → connect → discover → read
//  Capabilities can be exercised against a real watch. The polished
//  Connected/Connecting/Degraded/Disconnected status UI is P1-004; this is
//  intentionally bare.
//
//  Apache-2.0 © FuseMind contributors
//

import SwiftUI

@main
struct FuseMindApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
