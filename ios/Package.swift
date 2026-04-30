// swift-tools-version:5.9
//
// EmvReader Swift package — wraps CoreNFC NFCTagReaderSession against the
// shared KMP parser exported as Shared.xcframework. Built and tested
// independently of iosApp/ (the existing SwiftUI sample app); iosApp consumes
// this package only when sample integration lands as a separate issue.
//
// Local development:
//   1. From repo root: ./gradlew :shared:assembleSharedReleaseXCFramework
//      (or :shared:assembleSharedDebugXCFramework for faster iteration).
//   2. Open ios/Package.swift in Xcode; the binaryTarget below resolves
//      to the freshly-built XCFramework.
//   3. Run tests: swift test --package-path ios
//      (or use Xcode's test runner against the EmvReaderTests target).

import PackageDescription

let package = Package(
    name: "EmvReader",
    platforms: [.iOS(.v14)],
    products: [
        .library(name: "EmvReader", targets: ["EmvReader"]),
    ],
    targets: [
        .binaryTarget(
            name: "Shared",
            path: "../shared/build/XCFrameworks/release/Shared.xcframework"
        ),
        .target(
            name: "EmvReader",
            dependencies: ["Shared"],
            path: "Sources/EmvReader"
        ),
        .testTarget(
            name: "EmvReaderTests",
            dependencies: ["EmvReader"],
            path: "Tests/EmvReaderTests"
        ),
    ]
)
