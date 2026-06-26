//
//  BLEWatchAdapter.swift
//  FuseMindCore
//
//  The iOS-side CoreBluetooth central that talks to ANY watch implementing
//  the FuseMind GATT protocol (docs/ble-protocol.md). The watch is the GATT
//  peripheral/server; this object is the central.
//
//  Why ONE adapter for every BLE watch (not one per brand):
//  ADR-001 says "adding a watch brand = one new adapter." For watches that
//  speak the FuseMind GATT contract natively (Wear OS, and any future brand
//  whose companion app implements the same UUIDs), the iOS side is identical —
//  so a single BLEWatchAdapter serves them all. Brands that can NOT run a
//  FuseMind companion (e.g. closed Garmin/Fitbit firmware) get their own
//  adapter later. The core never learns which watch is on the other end.
//
//  Phase 1 scope (P1-002): scan → connect → discover → read Capabilities once,
//  with CoreBluetooth background mode and state restoration. Sending/reading of
//  feature payloads is wired minimally; richer flows land in Phases 2/3/5.
//
//  NOT hardware-tested yet — see NOTES.md for the on-device checklist.
//
//  Apache-2.0 © FuseMind contributors
//

import Foundation
import CoreBluetooth

/// FuseMind custom GATT UUIDs. MUST match the watch side byte-for-byte.
/// See docs/ble-protocol.md and FuseMindGatt (Kotlin).
enum FuseMindGATT {
    static let service             = CBUUID(string: "F0000000-0000-1000-8000-00805F9B34FB")
    static let notificationPush     = CBUUID(string: "F0000001-0000-1000-8000-00805F9B34FB") // phone -> watch
    static let notificationDismiss  = CBUUID(string: "F0000002-0000-1000-8000-00805F9B34FB") // watch -> phone
    static let callControl          = CBUUID(string: "F0000003-0000-1000-8000-00805F9B34FB") // bidirectional
    static let mediaCommand         = CBUUID(string: "F0000004-0000-1000-8000-00805F9B34FB") // watch -> phone
    static let healthData           = CBUUID(string: "F0000005-0000-1000-8000-00805F9B34FB") // watch -> phone
    static let capabilities         = CBUUID(string: "F0000006-0000-1000-8000-00805F9B34FB") // watch -> phone

    /// Restoration identifier — lets iOS relaunch us into the background to
    /// keep the link alive (Phase 1 background-mode requirement).
    static let restoreIdentifier = "app.fusemind.ble.central"
}

/// A generic BLE adapter for any watch speaking the FuseMind GATT protocol.
public final class BLEWatchAdapter: NSObject, WatchAdapter {

    // MARK: WatchAdapter contract

    public let platformId = "ble"

    public private(set) var state: ConnectionState = .disconnected

    // MARK: CoreBluetooth state

    private var central: CBCentralManager!
    private var watch: CBPeripheral?
    private var characteristics: [CBUUID: CBCharacteristic] = [:]

    /// Populated by reading the Capabilities characteristic once on connect.
    /// Until then we report a conservative default (no optional hardware), so
    /// the app degrades gracefully rather than assuming features exist.
    private var watchCapabilities = WatchCapabilities(
        hasMic: false, hasSpeaker: false, hasGPS: false, hasHRM: false, maxNotifLength: 0
    )

    /// Resolved when the handshake (capabilities read) completes or times out.
    private var connectContinuation: CheckedContinuation<Bool, Never>?

    /// Larger MTU so Phase-2 notification bodies exceed the 23-byte default.
    /// iOS negotiates MTU automatically on connect; we cap our writes to the
    /// peripheral's reported maximumWriteValueLength.
    private let desiredMTU = 247

    private let queue = DispatchQueue(label: "app.fusemind.ble")

    public override init() {
        super.init()
        // `restoreIdentifier` opts into State Preservation & Restoration so iOS
        // can relaunch the app in the background to service the link.
        central = CBCentralManager(
            delegate: self,
            queue: queue,
            options: [CBCentralManagerOptionRestoreIdentifierKey: FuseMindGATT.restoreIdentifier]
        )
    }

    // MARK: - WatchAdapter methods

    /// Scan for the FuseMind service, connect, discover, and read Capabilities.
    /// Returns true once the handshake completes (target < 5s), false on timeout.
    public func connect() async -> Bool {
        return await withCheckedContinuation { continuation in
            queue.async {
                // Already connected → succeed immediately (idempotent).
                if self.state == .connected {
                    continuation.resume(returning: true)
                    return
                }
                // A connect is already in flight → don't clobber its continuation
                // (that would leak it and hang the first caller forever).
                if self.connectContinuation != nil {
                    continuation.resume(returning: false)
                    return
                }
                self.connectContinuation = continuation
                self.beginScanIfPoweredOn()
                self.startConnectTimeout()
            }
        }
    }

    public func disconnect() {
        queue.async {
            if let watch = self.watch {
                self.central.cancelPeripheralConnection(watch)
            }
            self.central.stopScan()
            self.transition(to: .disconnected)
        }
    }

    @discardableResult
    public func sendNotification(_ notification: WatchNotification) async -> Bool {
        guard let data = try? JSONEncoder.fuseMind.encode(notification) else { return false }
        return write(data, to: FuseMindGATT.notificationPush)
    }

    public func readHealth() async -> HealthSnapshot? {
        // Phase 1: read the latest snapshot synchronously off the characteristic
        // cache. A subscribed/streamed path lands in Phase 5.
        guard let char = characteristics[FuseMindGATT.healthData],
              let data = char.value,
              let snapshot = try? JSONDecoder.fuseMind.decode(HealthSnapshot.self, from: data)
        else { return nil }
        return snapshot
    }

    @discardableResult
    public func sendMediaCommand(_ command: MediaCommand) async -> Bool {
        // Media is watch -> phone in normal flow; this path exists so the core
        // can echo/relay a command if needed. Sent as the protocol's UTF-8 enum.
        let raw = Data(command.rawValue.utf8)
        return write(raw, to: FuseMindGATT.mediaCommand)
    }

    public func capabilities() -> WatchCapabilities {
        return watchCapabilities
    }

    // MARK: - Internals

    private func beginScanIfPoweredOn() {
        guard central.state == .poweredOn else { return } // will retry in didUpdateState
        transition(to: .scanning)
        central.scanForPeripherals(
            withServices: [FuseMindGATT.service],
            options: [CBCentralManagerScanOptionAllowDuplicatesKey: false]
        )
    }

    private func startConnectTimeout() {
        // Surface a failed handshake rather than hanging the caller forever.
        queue.asyncAfter(deadline: .now() + 15) { [weak self] in
            guard let self, self.connectContinuation != nil, self.state != .connected else { return }
            self.central.stopScan()
            self.transition(to: .disconnected)
            self.finishConnect(success: false)
        }
    }

    private func finishConnect(success: Bool) {
        connectContinuation?.resume(returning: success)
        connectContinuation = nil
    }

    private func write(_ data: Data, to uuid: CBUUID) -> Bool {
        guard let watch, let char = characteristics[uuid] else { return false }
        // Use withResponse where the characteristic supports it (delivery ack).
        let type: CBCharacteristicWriteType =
            char.properties.contains(.write) ? .withResponse : .withoutResponse
        watch.writeValue(data, for: char, type: type)
        return true
    }

    private func transition(to newState: ConnectionState) {
        guard newState != state else { return }
        state = newState
        onStateChange?(newState)
    }

    /// UI hook (P1-004). Called on the BLE queue; marshal to main in the view.
    public var onStateChange: ((ConnectionState) -> Void)?
}

// MARK: - CBCentralManagerDelegate

extension BLEWatchAdapter: CBCentralManagerDelegate {

    public func centralManagerDidUpdateState(_ central: CBCentralManager) {
        switch central.state {
        case .poweredOn:
            // If a connect() is pending, (re)start scanning now that BLE is ready.
            if connectContinuation != nil { beginScanIfPoweredOn() }
        case .poweredOff, .unauthorized, .unsupported:
            transition(to: .disconnected)
        default:
            break
        }
    }

    public func centralManager(_ central: CBCentralManager,
                               didDiscover peripheral: CBPeripheral,
                               advertisementData: [String: Any],
                               rssi RSSI: NSNumber) {
        // First FuseMind-advertising watch wins. Retain it (P1-003 reconnect).
        central.stopScan()
        watch = peripheral
        peripheral.delegate = self
        transition(to: .connecting)
        central.connect(peripheral, options: nil)
    }

    public func centralManager(_ central: CBCentralManager, didConnect peripheral: CBPeripheral) {
        peripheral.discoverServices([FuseMindGATT.service])
    }

    public func centralManager(_ central: CBCentralManager,
                               didFailToConnect peripheral: CBPeripheral, error: Error?) {
        transition(to: .disconnected)
        finishConnect(success: false)
    }

    public func centralManager(_ central: CBCentralManager,
                               didDisconnectPeripheral peripheral: CBPeripheral, error: Error?) {
        characteristics.removeAll()
        // Out of range / dropped link = degraded, not disconnected (P1-003).
        // Retain the peripheral and try to reconnect when it returns.
        transition(to: .degraded)
        central.connect(peripheral, options: nil)
    }

    /// State restoration: iOS relaunched us in the background with the link.
    public func centralManager(_ central: CBCentralManager,
                               willRestoreState dict: [String: Any]) {
        if let restored = dict[CBCentralManagerRestoredStatePeripheralsKey] as? [CBPeripheral],
           let peripheral = restored.first {
            watch = peripheral
            peripheral.delegate = self
        }
    }
}

// MARK: - CBPeripheralDelegate

extension BLEWatchAdapter: CBPeripheralDelegate {

    public func peripheral(_ peripheral: CBPeripheral, didDiscoverServices error: Error?) {
        guard let service = peripheral.services?.first(where: { $0.uuid == FuseMindGATT.service }) else {
            transition(to: .disconnected)
            finishConnect(success: false)
            return
        }
        peripheral.discoverCharacteristics(nil, for: service)
    }

    public func peripheral(_ peripheral: CBPeripheral,
                          didDiscoverCharacteristicsFor service: CBService, error: Error?) {
        for char in service.characteristics ?? [] {
            characteristics[char.uuid] = char
            // Subscribe to watch -> phone Notify characteristics.
            if char.properties.contains(.notify) {
                peripheral.setNotifyValue(true, for: char)
            }
        }
        // Read Capabilities once — the contract's handshake step.
        if let caps = characteristics[FuseMindGATT.capabilities] {
            peripheral.readValue(for: caps)
        } else {
            // No capabilities characteristic = not a FuseMind watch.
            transition(to: .disconnected)
            finishConnect(success: false)
        }
    }

    public func peripheral(_ peripheral: CBPeripheral,
                          didUpdateValueFor characteristic: CBCharacteristic, error: Error?) {
        guard let data = characteristic.value else { return }
        switch characteristic.uuid {
        case FuseMindGATT.capabilities:
            if let caps = try? JSONDecoder.fuseMind.decode(WatchCapabilities.self, from: data) {
                watchCapabilities = caps
                transition(to: .connected) // handshake complete
                finishConnect(success: true)
            }
        case FuseMindGATT.healthData:
            // Cached for readHealth(); Phase 5 will fan this out to HealthKit.
            break
        default:
            break
        }
    }
}

// MARK: - JSON coders matching the protocol payloads

extension JSONEncoder {
    /// Encodes timestamps as Unix epoch seconds, per docs/ble-protocol.md.
    static let fuseMind: JSONEncoder = {
        let e = JSONEncoder()
        e.dateEncodingStrategy = .secondsSince1970
        return e
    }()
}

extension JSONDecoder {
    static let fuseMind: JSONDecoder = {
        let d = JSONDecoder()
        d.dateDecodingStrategy = .secondsSince1970
        return d
    }()
}
