import Foundation
@testable import nfc_emv_toolkit

// NOTE: This file is currently NOT wired into a PBXNativeTarget. See
// follow-up issue #67 for the test-target wiring task. Sources are
// kept in `iosAppTests/` so they appear in the source tree, version
// with the production code, and can be added to the target by file
// inclusion (no source-move needed) once the target exists.

/// Test double for ``NfcAvailability``. Lets a test pin a status and
/// flip it later to drive ``ReaderViewModel/refreshAvailability()``.
final class FakeNfcAvailability: NfcAvailability, @unchecked Sendable {

    private let lock = NSLock()
    private var status: NfcAvailabilityStatus

    init(_ status: NfcAvailabilityStatus) {
        self.status = status
    }

    func current() -> NfcAvailabilityStatus {
        lock.lock()
        defer { lock.unlock() }
        return status
    }

    func set(_ status: NfcAvailabilityStatus) {
        lock.lock()
        defer { lock.unlock() }
        self.status = status
    }
}
