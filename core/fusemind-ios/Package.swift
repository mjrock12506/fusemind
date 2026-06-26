// swift-tools-version:5.9
//
// FuseMindCore — the platform-agnostic "brain" of FuseMind on iOS.
// This SPM package builds the universal WatchAdapter contract and the
// concrete BLEWatchAdapter (CoreBluetooth). The SwiftUI app shell (P1-004)
// links against this package.
//
// Apache-2.0 © FuseMind contributors

import PackageDescription

let package = Package(
    name: "FuseMindCore",
    platforms: [
        .iOS(.v15),
        .macOS(.v12) // so CI can `swift build` the core on macOS runners
    ],
    products: [
        .library(name: "FuseMindCore", targets: ["FuseMindCore"])
    ],
    targets: [
        .target(name: "FuseMindCore", path: "Sources/FuseMindCore")
    ]
)
