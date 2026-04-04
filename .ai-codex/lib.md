# Library Exports (generated 2026-04-03)
# fn=function, class=class

## app/src/main/kotlin/com/pulsecoach/
MainActivity.kt
  class MainActivity
    fn onCreate

## app/src/main/kotlin/com/pulsecoach/ble/
PolarBleManager.kt
  class PolarBleManager
    fn deviceConnected
    fn deviceDisconnected
    fn deviceConnecting
    fn bleSdkFeatureReady
    fn disInformationReceived
    fn batteryLevelReceived
    fn scanForDevices
    fn connectToDevice
    fn disconnectFromDevice
    fn streamHeartRate
    fn shutdown

## app/src/main/kotlin/com/pulsecoach/data/
HrSampleDao.kt
  interface HrSampleDao
    fn insert
    fn getSamplesForSession
    fn getSamplesForSessionOnce
    fn deleteBySessionIds
    fn getSamplesForSessions
HrSampleEntity.kt
  class HrSampleEntity
PulseCoachDatabase.kt
  class PulseCoachDatabase
    fn migrate
    fn migrate
    fn migrate
    fn migrate
    fn getInstance
SessionDao.kt
  interface SessionDao
    fn insert
    fn update
    fn getAllSessions
    fn getSessionById
    fn deleteByIds
    fn getQualifyingSessions
    fn getQualifyingSessionsByType
    fn updateSessionType
SessionEntity.kt
  class SessionEntity
ZoneConfigDao.kt
  interface ZoneConfigDao
    fn getZoneConfig
    fn upsert
ZoneConfigEntity.kt
  class ZoneConfigEntity

## app/src/main/kotlin/com/pulsecoach/model/
BleConnectionState.kt
  class BleConnectionState
  class Disconnected
  class Scanning
  class DevicesFound
  class Connecting
  class Connected
  class Error
  class FoundDevice
HrReading.kt
  class HrReading
HrSample.kt
  class HrSample
Session.kt
  class Session
SessionType.kt
  class SessionType
    fn fromString
UserProfile.kt
  class BiologicalSex
  class UserProfile
ZoneConfig.kt
  class ZoneConfig

## app/src/main/kotlin/com/pulsecoach/repository/
SessionRepository.kt
  fn startSession
  fn finishSession
  fn insertSample
  fn getAllSessions
  fn getQualifyingSessions
  fn getQualifyingSessionsByType
  fn updateSessionType
  fn getSamplesForSessions
  fn getSamplesForSession
  fn getSamplesForSessionOnce
  fn deleteSessions
  fn seedSyntheticSessions
  fn seedRealisticSessions
  class SessionRepository
UserProfileRepository.kt
  class UserProfileRepository
    fn isProfileComplete
    fn getProfile
    fn saveProfile
ZoneConfigRepository.kt
  class ZoneConfigRepository
    fn saveZoneConfig

## app/src/main/kotlin/com/pulsecoach/ui/
EvaluationScreen.kt
  fn EvaluationScreen
LiveCalorieChart.kt
  fn LiveCalorieChart
LiveHrChart.kt
  fn LiveHrChart
LiveSessionScreen.kt
  fn LiveSessionScreen
  fn formatTime
ProfileSetupScreen.kt
  fn ProfileSetupScreen
SessionHistoryScreen.kt
  fn SessionHistoryScreen
SettingsScreen.kt
  fn SettingsScreen

## app/src/main/kotlin/com/pulsecoach/util/
CalorieCalculator.kt
  class CalorieCalculator
    fn calPerMinute
    fn calPerSample
CsvExporter.kt
  class CsvExporter
    fn buildCsv
    fn fileName
HistoricalAverager.kt
  class HistoricalAverager
    fn durationBucketFor
    fn getFilteredCurve
    fn buildCurve
    fn blend
PolynomialProjector.kt
  class PolynomialProjector
    fn project
    fn fitQuadratic
    fn gaussianElimination
ProjectionCalibrator.kt
  class ProjectionCalibrator
    fn updateFactor
    fn getCorrectionFactor
    fn applyTo
    fn getProjectionSigma
    fn computeRollingMean
    fn computeSigma
    fn seedCalibrationRatios
    fn interpolateProjection
SyntheticSessionGenerator.kt
  class SyntheticSessionGenerator
    fn generate
  class Result
ZoneCalculator.kt
  class ZoneCalculator
    fn zoneForBpm
    fn colorForZone
    fn karvonenZones
    fn textColorForZone
    fn nameForZone

## app/src/main/kotlin/com/pulsecoach/viewmodel/
EvaluationViewModel.kt
  class WindowMetrics
  class TestCurveData
  class TypedMetrics
  class EvaluationResult
  class EvaluationViewModel
    fn runEvaluation
    fn buildMinuteCurve
LiveSessionViewModel.kt
  class LiveSessionViewModel
    fn setSessionType
    fn startScan
    fn stopScan
    fn connectToDevice
    fn disconnect
    fn setTargetDuration
    fn startRecording
    fn stopRecording
    fn onCleared
ProfileViewModel.kt
  class ProfileViewModel
    fn onAgeChange
    fn onWeightChange
    fn onSexChange
    fn onRestingHrChange
    fn onMaxHrChange
    fn saveProfile
SessionHistoryViewModel.kt
  class SeedingState
  class Idle
  class InProgress
  class Done
  class Error
  class RealisticSeedingState
  class Idle
  class InProgress
  class Done
  class Error
  class ExportResult
  class Success
  class Error
  class SessionHistoryViewModel
    fn exportToCsv
    fn clearExportResult
    fn seedSyntheticSessions
    fn clearSeedingState
    fn seedRealisticSessions
    fn seedCalibrationRatios
    fn clearRealisticSeedingState
    fn toggleSelection
    fn clearSelection
    fn updateSessionType
    fn deleteSelected
SettingsViewModel.kt
  class SettingsViewModel
    fn saveZoneConfig
    fn resetToDefaults
    fn karvonenZonesOrNull
