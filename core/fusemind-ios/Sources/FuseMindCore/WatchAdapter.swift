//
//  WatchAdapter.swift
//  FuseMindCore
//
//  The universal contract every watch platform implements.
//  The iOS app and AI agent talk ONLY to this protocol — they never
//  know whether a Samsung (Wear OS), Garmin, Fitbit, or Amazfit watch
//  is on the other end.
//
//  Design: Strategy Pattern + Hardware Abstraction Layer (HAL).
//  See docs/adr/ADR-001-adapter-architecture.md
//
//  Apache-2.0 © FuseMind contributors
//

import Foundation

// MARK: - Value Types (the universal data model)

/// Priority assigned by the AI agent to a notification.
public enum Priority: String, Codable {
    case high = "H"
    case medium = "M"
    case low = "L"
}

/// A single notification, normalised across all platforms.
public struct WatchNotification: Codable, Identifiable {
    public let id: String          // ANCS UID or platform notification id
    public let appName: String
    public let title: String
    public let body: String
    public var aiPriority: Priority
    public let timestamp: Date

    public init(id: String, appName: String, title: String, body: String,
                aiPriority: Priority = .medium, timestamp: Date = Date()) {
        self.id = id
        self.appName = appName
        self.title = title
        self.body = body
        self.aiPriority = aiPriority
        self.timestamp = timestamp
    }
}

/// A point-in-time health reading, normalised across all platforms.
/// `sourceAdapter` records WHICH platform produced it (e.g. "wear_os",
/// "garmin") so the AI agent can do cross-device insights.
public struct HealthSnapshot: Codable {
    public let heartRate: Int?
    public let hrv: Double?
    public let steps: Int?
    public let sourceAdapter: String
    public let recordedAt: Date

    public init(heartRate: Int? = nil, hrv: Double? = nil, steps: Int? = nil,
                sourceAdapter: String, recordedAt: Date = Date()) {
        self.heartRate = heartRate
        self.hrv = hrv
        self.steps = steps
        self.sourceAdapter = sourceAdapter
        self.recordedAt = recordedAt
    }
}

/// Media transport commands sent from the watch to the phone.
public enum MediaCommand: String, Codable {
    case play, pause, next, previous, volumeUp, volumeDown
}

/// What a given watch can physically do. The iOS app checks this BEFORE
/// using a feature, so a watch with no speaker (e.g. Garmin) gracefully
/// degrades instead of failing.
public struct WatchCapabilities: Codable {
    public let hasMic: Bool
    public let hasSpeaker: Bool
    public let hasGPS: Bool
    public let hasHRM: Bool          // heart-rate monitor
    public let maxNotifLength: Int   // characters the watch UI can show

    public init(hasMic: Bool, hasSpeaker: Bool, hasGPS: Bool,
                hasHRM: Bool, maxNotifLength: Int) {
        self.hasMic = hasMic
        self.hasSpeaker = hasSpeaker
        self.hasGPS = hasGPS
        self.hasHRM = hasHRM
        self.maxNotifLength = maxNotifLength
    }
}

/// Connection lifecycle, surfaced to the UI.
public enum ConnectionState: String {
    case disconnected, scanning, connecting, connected, degraded
}

// MARK: - The Contract

/// Every watch platform provides a type conforming to `WatchAdapter`.
/// Adding a new brand = one new conforming type. The core never changes.
public protocol WatchAdapter: AnyObject {

    /// Stable platform id, e.g. "wear_os", "garmin", "fitbit", "amazfit".
    var platformId: String { get }

    /// Current connection state (observable by the UI layer).
    var state: ConnectionState { get }

    /// Begin advertising/scanning and establish a link to the watch.
    /// - Returns: `true` if a connection was established.
    func connect() async -> Bool

    /// Tear down the link cleanly.
    func disconnect()

    /// Push a notification to the watch for display.
    /// - Returns: `true` if delivery was acknowledged.
    @discardableResult
    func sendNotification(_ notification: WatchNotification) async -> Bool

    /// Read the latest health snapshot from the watch's sensors.
    func readHealth() async -> HealthSnapshot?

    /// Relay a media transport command originating on the watch.
    @discardableResult
    func sendMediaCommand(_ command: MediaCommand) async -> Bool

    /// Report what this watch can physically do.
    func capabilities() -> WatchCapabilities
}

// MARK: - Convenience

public extension WatchAdapter {
    /// True when the watch can carry a full voice call (mic + speaker).
    func canRouteCallAudio() -> Bool {
        let caps = capabilities()
        return caps.hasMic && caps.hasSpeaker
    }
}
