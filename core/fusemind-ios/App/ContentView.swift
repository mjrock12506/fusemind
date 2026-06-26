//
//  ContentView.swift
//  FuseMind (iOS app target)
//
//  P1-002 test harness UI: a Connect button, the live ConnectionState, and the
//  WatchCapabilities read back from the watch on handshake. Talks ONLY to the
//  WatchAdapter contract — it never knows the watch is a Wear OS device.
//
//  Apache-2.0 © FuseMind contributors
//

import SwiftUI
import FuseMindCore

/// Bridges the BLEWatchAdapter (callbacks on a background BLE queue) to SwiftUI
/// state on the main actor. The view observes this; it never touches BLE directly.
@MainActor
final class WatchLinkViewModel: ObservableObject {
    @Published var state: ConnectionState = .disconnected
    @Published var capabilities: WatchCapabilities?

    /// The core adapter. Typed as the protocol on purpose — swapping in a
    /// different watch platform later changes only this one line.
    private let adapter: BLEWatchAdapter

    init() {
        adapter = BLEWatchAdapter()
        adapter.onStateChange = { [weak self] newState in
            // onStateChange fires on the BLE queue; hop to main for SwiftUI.
            Task { @MainActor in
                guard let self else { return }
                self.state = newState
                self.capabilities = (newState == .connected) ? self.adapter.capabilities() : nil
            }
        }
    }

    func connect() {
        Task { _ = await adapter.connect() }
    }

    func disconnect() {
        adapter.disconnect()
    }
}

struct ContentView: View {
    @StateObject private var model = WatchLinkViewModel()

    var body: some View {
        VStack(spacing: 28) {
            Text("FuseMind")
                .font(.largeTitle).bold()

            statusBadge

            Button(model.state == .disconnected ? "Connect watch" : "Disconnect") {
                model.state == .disconnected ? model.connect() : model.disconnect()
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.large)

            if let caps = model.capabilities {
                capabilitiesCard(caps)
            }

            Spacer()
        }
        .padding()
    }

    private var statusBadge: some View {
        HStack(spacing: 10) {
            Circle().fill(color(for: model.state)).frame(width: 14, height: 14)
            Text(label(for: model.state)).font(.headline)
        }
    }

    private func capabilitiesCard(_ caps: WatchCapabilities) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("Watch capabilities").font(.subheadline).bold()
            row("Microphone", caps.hasMic)
            row("Speaker", caps.hasSpeaker)
            row("GPS", caps.hasGPS)
            row("Heart-rate monitor", caps.hasHRM)
            Text("Max notification length: \(caps.maxNotifLength)")
                .font(.caption).foregroundStyle(.secondary)
        }
        .padding()
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(.quaternary, in: RoundedRectangle(cornerRadius: 12))
    }

    private func row(_ name: String, _ on: Bool) -> some View {
        HStack {
            Image(systemName: on ? "checkmark.circle.fill" : "xmark.circle")
                .foregroundStyle(on ? .green : .secondary)
            Text(name)
        }
        .font(.callout)
    }

    private func label(for state: ConnectionState) -> String {
        switch state {
        case .disconnected: return "Disconnected"
        case .scanning:     return "Scanning…"
        case .connecting:   return "Connecting…"
        case .connected:    return "Connected"
        case .degraded:     return "Degraded (reconnecting)"
        }
    }

    private func color(for state: ConnectionState) -> Color {
        switch state {
        case .disconnected: return .gray
        case .scanning, .connecting: return .orange
        case .connected: return .green
        case .degraded: return .yellow
        }
    }
}
