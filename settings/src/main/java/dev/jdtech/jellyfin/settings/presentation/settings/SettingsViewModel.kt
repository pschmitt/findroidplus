package dev.jdtech.jellyfin.settings.presentation.settings

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.jdtech.jellyfin.settings.R
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import dev.jdtech.jellyfin.settings.presentation.enums.DeviceType
import dev.jdtech.jellyfin.settings.presentation.models.PreferenceAppLanguage
import dev.jdtech.jellyfin.settings.presentation.models.PreferenceCategory
import dev.jdtech.jellyfin.settings.presentation.models.PreferenceFileEdit
import dev.jdtech.jellyfin.settings.presentation.models.PreferenceGroup
import dev.jdtech.jellyfin.settings.presentation.models.PreferenceIntInput
import dev.jdtech.jellyfin.settings.presentation.models.PreferenceLongInput
import dev.jdtech.jellyfin.settings.presentation.models.PreferenceMultiSelect
import dev.jdtech.jellyfin.settings.presentation.models.PreferenceSelect
import dev.jdtech.jellyfin.settings.presentation.models.PreferenceSwitch
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

private val CONCRETE_DOWNLOAD_LOCATIONS = setOf("internal", "external")

@HiltViewModel
class SettingsViewModel
@Inject
constructor(
    private val appPreferences: AppPreferences,
    @param:ApplicationContext private val context: Context,
) : ViewModel() {
    private val _state = MutableStateFlow(SettingsState())
    val state = _state.asStateFlow()

    /**
     * "Internal"/"External"/"Ask" only makes sense to offer a choice when a removable volume
     * (SD card/USB) actually exists - on a device with only built-in storage there's nothing to
     * choose between, so the whole preference is disabled rather than left offering options that
     * don't do anything different from each other. Duplicates the removable-volume check in
     * core/utils/DownloadStorage.kt rather than depending on it - `core` depends on `settings`,
     * not the other way around, so reusing it here isn't possible.
     */
    private fun hasRemovableStorage(): Boolean =
        context.getExternalFilesDirs(null).any { it != null && Environment.isExternalStorageRemovable(it) }

    /**
     * Same idea for the mobile-data/roaming download switches: on a device without cellular
     * connectivity (e.g. a Wi-Fi-only tablet) they can't ever do anything, so disable them
     * instead of offering dead options.
     */
    private fun hasCellularConnectivity(): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)

    private val eventsChannel = Channel<SettingsEvent>()
    val events = eventsChannel.receiveAsFlow()

    private val topLevelPreferences =
        listOf(
            PreferenceGroup(
                preferences =
                    listOf(
                        PreferenceSwitch(
                            nameStringResource = R.string.offline_mode,
                            descriptionStringRes = R.string.offline_mode_summary,
                            iconDrawableId = R.drawable.ic_server_off,
                            supportedDeviceTypes = listOf(DeviceType.PHONE),
                            onClick = {
                                viewModelScope.launch {
                                    eventsChannel.send(SettingsEvent.RestartActivity)
                                }
                            },
                            backendPreference = appPreferences.offlineMode,
                        )
                    )
            ),
            PreferenceGroup(
                preferences =
                    listOf(
                        PreferenceCategory(
                            nameStringResource = R.string.settings_category_language,
                            descriptionStringRes = R.string.settings_category_language_summary,
                            iconDrawableId = R.drawable.ic_languages,
                            onClick = {
                                viewModelScope.launch {
                                    eventsChannel.send(
                                        SettingsEvent.NavigateToSettings(
                                            intArrayOf(it.nameStringResource)
                                        )
                                    )
                                }
                            },
                            nestedPreferenceGroups =
                                listOf(
                                    PreferenceGroup(
                                        preferences =
                                            listOf(
                                                PreferenceAppLanguage(
                                                    nameStringResource = R.string.app_language,
                                                    descriptionStringRes =
                                                        R.string.app_language_summary,
                                                    iconDrawableId = R.drawable.ic_languages,
                                                    enabled =
                                                        Build.VERSION.SDK_INT >=
                                                            Build.VERSION_CODES.TIRAMISU,
                                                )
                                            )
                                    ),
                                    PreferenceGroup(
                                        preferences =
                                            listOf(
                                                PreferenceSelect(
                                                    nameStringResource =
                                                        R.string.settings_preferred_audio_language,
                                                    descriptionStringRes =
                                                        R.string
                                                            .settings_preferred_audio_language_summary,
                                                    iconDrawableId = R.drawable.ic_speaker,
                                                    backendPreference =
                                                        appPreferences.preferredAudioLanguage,
                                                    options = R.array.languages,
                                                    optionValues = R.array.languages_values,
                                                    optionsIncludeNull = true,
                                                ),
                                                PreferenceSelect(
                                                    nameStringResource =
                                                        R.string
                                                            .settings_preferred_subtitle_language,
                                                    descriptionStringRes =
                                                        R.string
                                                            .settings_preferred_subtitle_language_summary,
                                                    iconDrawableId = R.drawable.ic_closed_caption,
                                                    backendPreference =
                                                        appPreferences.preferredSubtitleLanguage,
                                                    options = R.array.languages,
                                                    optionValues = R.array.languages_values,
                                                    optionsIncludeNull = true,
                                                ),
                                            )
                                    ),
                                ),
                        )
                    )
            ),
            PreferenceGroup(
                preferences =
                    listOf(
                        PreferenceCategory(
                            nameStringResource = R.string.settings_category_interface,
                            descriptionStringRes = R.string.settings_category_interface_summary,
                            iconDrawableId = R.drawable.ic_layout_dashboard,
                            onClick = {
                                viewModelScope.launch {
                                    eventsChannel.send(
                                        SettingsEvent.NavigateToSettings(
                                            intArrayOf(it.nameStringResource)
                                        )
                                    )
                                }
                            },
                            nestedPreferenceGroups =
                                listOf(
                                    PreferenceGroup(
                                        nameStringResource = R.string.settings_category_appearance,
                                        preferences =
                                            listOf(
                                                PreferenceSelect(
                                                    nameStringResource = R.string.theme,
                                                    descriptionStringRes = R.string.theme_summary,
                                                    iconDrawableId = R.drawable.ic_palette,
                                                    supportedDeviceTypes = listOf(DeviceType.PHONE),
                                                    backendPreference = appPreferences.theme,
                                                    onUpdate = { value ->
                                                        viewModelScope.launch {
                                                            eventsChannel.send(
                                                                SettingsEvent.UpdateTheme(
                                                                    value ?: "system"
                                                                )
                                                            )
                                                        }
                                                    },
                                                    options = R.array.theme,
                                                    optionValues = R.array.theme_values,
                                                ),
                                                PreferenceSwitch(
                                                    nameStringResource = R.string.dynamic_colors,
                                                    descriptionStringRes =
                                                        R.string.dynamic_colors_summary,
                                                    iconDrawableId = R.drawable.ic_brush,
                                                    enabled =
                                                        Build.VERSION.SDK_INT >=
                                                            Build.VERSION_CODES.S,
                                                    supportedDeviceTypes = listOf(DeviceType.PHONE),
                                                    backendPreference = appPreferences.dynamicColors,
                                                ),
                                                PreferenceSelect(
                                                    nameStringResource = R.string.date_format,
                                                    descriptionStringRes =
                                                        R.string.date_format_summary,
                                                    iconDrawableId = R.drawable.ic_calendar,
                                                    supportedDeviceTypes = listOf(DeviceType.PHONE),
                                                    backendPreference = appPreferences.dateFormat,
                                                    options = R.array.date_format,
                                                    optionValues = R.array.date_format_values,
                                                ),
                                            ),
                                    ),
                                    PreferenceGroup(
                                        nameStringResource = R.string.home,
                                        preferences =
                                            listOf(
                                                PreferenceSwitch(
                                                    nameStringResource = R.string.home_suggestions,
                                                    descriptionStringRes =
                                                        R.string.home_suggestions_summary,
                                                    iconDrawableId = R.drawable.ic_sparkles,
                                                    backendPreference =
                                                        appPreferences.homeSuggestions,
                                                ),
                                                PreferenceSwitch(
                                                    nameStringResource =
                                                        R.string.home_continue_watching,
                                                    descriptionStringRes =
                                                        R.string.home_continue_watching_summary,
                                                    iconDrawableId = R.drawable.ic_play,
                                                    backendPreference =
                                                        appPreferences.homeContinueWatching,
                                                ),
                                                PreferenceSwitch(
                                                    nameStringResource = R.string.home_next_up,
                                                    descriptionStringRes =
                                                        R.string.home_next_up_summary,
                                                    iconDrawableId = R.drawable.ic_skip_forward,
                                                    backendPreference = appPreferences.homeNextUp,
                                                ),
                                                PreferenceSwitch(
                                                    nameStringResource = R.string.home_latest,
                                                    descriptionStringRes =
                                                        R.string.home_latest_summary,
                                                    iconDrawableId = R.drawable.ic_clock,
                                                    backendPreference = appPreferences.homeLatest,
                                                ),
                                                PreferenceSwitch(
                                                    nameStringResource = R.string.home_discover,
                                                    descriptionStringRes =
                                                        R.string.home_discover_summary,
                                                    iconDrawableId = R.drawable.ic_compass,
                                                    supportedDeviceTypes = listOf(DeviceType.PHONE),
                                                    backendPreference =
                                                        appPreferences.homeDiscover,
                                                ),
                                                PreferenceCategory(
                                                    nameStringResource =
                                                        R.string.settings_category_home_layout,
                                                    descriptionStringRes =
                                                        R.string.settings_home_layout_summary,
                                                    iconDrawableId = R.drawable.ic_arrow_down_up,
                                                    supportedDeviceTypes = listOf(DeviceType.PHONE),
                                                    onClick = {
                                                        viewModelScope.launch {
                                                            eventsChannel.send(
                                                                SettingsEvent.NavigateToHomeLayout
                                                            )
                                                        }
                                                    },
                                                ),
                                            ),
                                    ),
                                ),
                        )
                    )
            ),
            PreferenceGroup(
                preferences =
                    listOf(
                        PreferenceCategory(
                            nameStringResource = R.string.settings_category_player,
                            descriptionStringRes = R.string.settings_category_player_summary,
                            iconDrawableId = R.drawable.ic_play,
                            onClick = {
                                viewModelScope.launch {
                                    eventsChannel.send(
                                        SettingsEvent.NavigateToSettings(
                                            intArrayOf(it.nameStringResource)
                                        )
                                    )
                                }
                            },
                            nestedPreferenceGroups =
                                listOf(
                                    PreferenceGroup(
                                        preferences =
                                            listOf(
                                                PreferenceCategory(
                                                    nameStringResource = R.string.subtitles,
                                                    descriptionStringRes =
                                                        R.string.subtitles_summary,
                                                    iconDrawableId = R.drawable.ic_closed_caption,
                                                    onClick = {
                                                        viewModelScope.launch {
                                                            eventsChannel.send(
                                                                SettingsEvent.LaunchIntent(
                                                                    Intent(
                                                                        Settings
                                                                            .ACTION_CAPTIONING_SETTINGS
                                                                    )
                                                                )
                                                            )
                                                        }
                                                    },
                                                )
                                            )
                                    ),
                                    PreferenceGroup(
                                        preferences =
                                            listOf(
                                                PreferenceSelect(
                                                    nameStringResource = R.string.pref_player_backend,
                                                    descriptionStringRes =
                                                        R.string.pref_player_backend_summary,
                                                    iconDrawableId = R.drawable.ic_settings,
                                                    backendPreference = appPreferences.playerBackend,
                                                    options = R.array.player_backends,
                                                    optionValues = R.array.player_backends
                                                ),

                                            )
                                    ),
                                    PreferenceGroup(
                                        preferences =
                                            listOf(
                                                PreferenceCategory(
                                                    nameStringResource = R.string.mpv_options,
                                                    descriptionStringRes =
                                                        R.string.mpv_options_summary,
                                                    iconDrawableId =
                                                        R.drawable.ic_sliders_horizontal,
                                                    onClick = {
                                                        viewModelScope.launch {
                                                            eventsChannel.send(
                                                                SettingsEvent.NavigateToSettings(
                                                                    intArrayOf(R.string.settings_category_player, it.nameStringResource)
                                                                )
                                                            )
                                                        }
                                                    },
                                                    nestedPreferenceGroups =
                                                        listOf(
                                                            PreferenceGroup(
                                                                preferences =
                                                                    listOf(
                                                                        PreferenceSelect(
                                                                            nameStringResource =
                                                                                R.string.pref_player_mpv_hwdec,
                                                                            descriptionStringRes =
                                                                                R.string.pref_player_mpv_hwdec_summary,
                                                                            iconDrawableId =
                                                                                R.drawable.ic_cpu,
                                                                            backendPreference =
                                                                                appPreferences.playerMpvHwdec,
                                                                            options = R.array.mpv_hwdec,
                                                                            optionValues = R.array.mpv_hwdec,
                                                                        ),
                                                                        PreferenceSelect(
                                                                            nameStringResource =
                                                                                R.string.pref_player_mpv_vo,
                                                                            descriptionStringRes =
                                                                                R.string.pref_player_mpv_vo_summary,
                                                                            iconDrawableId =
                                                                                R.drawable
                                                                                    .ic_monitor,
                                                                            backendPreference = appPreferences.playerMpvVo,
                                                                            options = R.array.mpv_vos,
                                                                            optionValues = R.array.mpv_vos,
                                                                        ),
                                                                        PreferenceSelect(
                                                                            nameStringResource =
                                                                                R.string.pref_player_mpv_ao,
                                                                            descriptionStringRes =
                                                                                R.string.pref_player_mpv_ao_summary,
                                                                            iconDrawableId =
                                                                                R.drawable
                                                                                    .ic_speaker,
                                                                            backendPreference = appPreferences.playerMpvAo,
                                                                            options = R.array.mpv_aos,
                                                                            optionValues = R.array.mpv_aos,
                                                                        ),
                                                                    ),
                                                            ),
                                                            PreferenceGroup(
                                                                nameStringResource = R.string.advanced,
                                                                preferences =
                                                                    listOf(
                                                                        PreferenceFileEdit(
                                                                            nameStringResource = R.string.edit_file_title,
                                                                            descriptionStringRes = R.string.mpv_conf_edit_summary,
                                                                            iconDrawableId =
                                                                                R.drawable
                                                                                    .ic_file_text,
                                                                            filePath = "mpv/mpv.conf",
                                                                            onClick = {
                                                                                viewModelScope.launch {
                                                                                    eventsChannel.send(
                                                                                        SettingsEvent.NavigateToSettingsFileEdit(it.filePath)
                                                                                    )
                                                                                }
                                                                            }
                                                                        ),
                                                                        PreferenceFileEdit(
                                                                            nameStringResource = R.string.edit_file_title,
                                                                            descriptionStringRes = R.string.mpv_input_conf_edit_summary,
                                                                            iconDrawableId =
                                                                                R.drawable
                                                                                    .ic_keyboard,
                                                                            filePath = "mpv/input.conf",
                                                                            onClick = {
                                                                                viewModelScope.launch {
                                                                                    eventsChannel.send(
                                                                                        SettingsEvent.NavigateToSettingsFileEdit(it.filePath)
                                                                                    )
                                                                                }
                                                                            }
                                                                        )
                                                                    )
                                                            ),
                                                        )
                                                ),
                                            )
                                    ),
                                    PreferenceGroup(
                                        nameStringResource = R.string.gestures,
                                        preferences =
                                            listOf(
                                                PreferenceSwitch(
                                                    nameStringResource = R.string.player_gestures,
                                                    descriptionStringRes =
                                                        R.string.player_gestures_summary,
                                                    iconDrawableId = R.drawable.ic_hand,
                                                    backendPreference =
                                                        appPreferences.playerGestures,
                                                    supportedDeviceTypes = listOf(DeviceType.PHONE),
                                                ),
                                                PreferenceSwitch(
                                                    nameStringResource =
                                                        R.string.player_gestures_vb,
                                                    descriptionStringRes =
                                                        R.string.player_gestures_vb_summary,
                                                    iconDrawableId = R.drawable.ic_sun,
                                                    dependencies =
                                                        listOf(appPreferences.playerGestures),
                                                    supportedDeviceTypes = listOf(DeviceType.PHONE),
                                                    backendPreference =
                                                        appPreferences.playerGesturesVB,
                                                ),
                                                PreferenceSwitch(
                                                    nameStringResource =
                                                        R.string.player_gestures_seek,
                                                    descriptionStringRes =
                                                        R.string.player_gestures_seek_summary,
                                                    iconDrawableId =
                                                        R.drawable.ic_move_horizontal,
                                                    dependencies =
                                                        listOf(appPreferences.playerGestures),
                                                    supportedDeviceTypes = listOf(DeviceType.PHONE),
                                                    backendPreference =
                                                        appPreferences.playerGesturesSeek,
                                                ),
                                                PreferenceSwitch(
                                                    nameStringResource =
                                                        R.string.player_gestures_zoom,
                                                    descriptionStringRes =
                                                        R.string.player_gestures_zoom_summary,
                                                    iconDrawableId = R.drawable.ic_zoom_in,
                                                    dependencies =
                                                        listOf(appPreferences.playerGestures),
                                                    supportedDeviceTypes = listOf(DeviceType.PHONE),
                                                    backendPreference =
                                                        appPreferences.playerGesturesZoom,
                                                ),
                                                PreferenceSwitch(
                                                    nameStringResource =
                                                        R.string.player_gestures_chapter_skip,
                                                    descriptionStringRes =
                                                        R.string
                                                            .player_gestures_chapter_skip_summary,
                                                    iconDrawableId =
                                                        R.drawable.ic_chevrons_right,
                                                    dependencies =
                                                        listOf(appPreferences.playerGestures),
                                                    supportedDeviceTypes = listOf(DeviceType.PHONE),
                                                    backendPreference =
                                                        appPreferences.playerGesturesChapterSkip,
                                                ),
                                                PreferenceSwitch(
                                                    nameStringResource =
                                                        R.string.player_brightness_remember,
                                                    descriptionStringRes =
                                                        R.string.player_brightness_remember_summary,
                                                    iconDrawableId = R.drawable.ic_save,
                                                    dependencies =
                                                        listOf(
                                                            appPreferences.playerGestures,
                                                            appPreferences.playerGesturesVB,
                                                        ),
                                                    supportedDeviceTypes = listOf(DeviceType.PHONE),
                                                    backendPreference =
                                                        appPreferences
                                                            .playerGesturesBrightnessRemember,
                                                ),
                                                PreferenceSwitch(
                                                    nameStringResource =
                                                        R.string.player_start_maximized,
                                                    descriptionStringRes =
                                                        R.string.player_start_maximized_summary,
                                                    iconDrawableId = R.drawable.ic_maximize,
                                                    dependencies =
                                                        listOf(appPreferences.playerGestures),
                                                    supportedDeviceTypes = listOf(DeviceType.PHONE),
                                                    backendPreference =
                                                        appPreferences.playerGesturesStartMaximized,
                                                ),
                                            ),
                                    ),
                                    PreferenceGroup(
                                        nameStringResource = R.string.seeking,
                                        preferences =
                                            listOf(
                                                PreferenceLongInput(
                                                    nameStringResource =
                                                        R.string.seek_back_increment,
                                                    descriptionStringRes =
                                                        R.string.seek_back_increment_summary,
                                                    iconDrawableId = R.drawable.ic_rewind,
                                                    backendPreference =
                                                        appPreferences.playerSeekBackInc,
                                                    suffixRes = R.string.ms,
                                                ),
                                                PreferenceLongInput(
                                                    nameStringResource =
                                                        R.string.seek_forward_increment,
                                                    descriptionStringRes =
                                                        R.string.seek_forward_increment_summary,
                                                    iconDrawableId = R.drawable.ic_fast_forward,
                                                    backendPreference =
                                                        appPreferences.playerSeekForwardInc,
                                                    suffixRes = R.string.ms,
                                                ),
                                            ),
                                    ),
                                    PreferenceGroup(
                                        nameStringResource = R.string.media_segments,
                                        preferences =
                                            listOf(
                                                PreferenceSwitch(
                                                    nameStringResource =
                                                        R.string
                                                            .pref_player_media_segments_skip_button,
                                                    descriptionStringRes =
                                                        R.string
                                                            .pref_player_media_segments_skip_button_summary,
                                                    iconDrawableId = R.drawable.ic_skip_forward,
                                                    backendPreference =
                                                        appPreferences.playerMediaSegmentsSkipButton,
                                                ),
                                                PreferenceMultiSelect(
                                                    nameStringResource =
                                                        R.string
                                                            .pref_player_media_segments_skip_button_type,
                                                    descriptionStringRes =
                                                        R.string
                                                            .pref_player_media_segments_skip_button_type_summary,
                                                    iconDrawableId = R.drawable.ic_list,
                                                    dependencies =
                                                        listOf(
                                                            appPreferences
                                                                .playerMediaSegmentsSkipButton
                                                        ),
                                                    backendPreference =
                                                        appPreferences
                                                            .playerMediaSegmentsSkipButtonType,
                                                    options = R.array.media_segments_type,
                                                    optionValues =
                                                        R.array.media_segments_type_values,
                                                ),
                                                PreferenceLongInput(
                                                    nameStringResource =
                                                        R.string
                                                            .pref_player_media_segments_skip_button_duration,
                                                    descriptionStringRes =
                                                        R.string
                                                            .pref_player_media_segments_skip_button_duration_summary,
                                                    iconDrawableId = R.drawable.ic_timer,
                                                    dependencies =
                                                        listOf(
                                                            appPreferences
                                                                .playerMediaSegmentsSkipButton
                                                        ),
                                                    backendPreference =
                                                        appPreferences
                                                            .playerMediaSegmentsSkipButtonDuration,
                                                    suffixRes = R.string.seconds,
                                                ),
                                                PreferenceSwitch(
                                                    nameStringResource =
                                                        R.string
                                                            .pref_player_media_segments_auto_skip,
                                                    descriptionStringRes =
                                                        R.string
                                                            .pref_player_media_segments_auto_skip_summary,
                                                    iconDrawableId = R.drawable.ic_zap,
                                                    backendPreference =
                                                        appPreferences.playerMediaSegmentsAutoSkip,
                                                ),
                                                PreferenceSelect(
                                                    nameStringResource =
                                                        R.string
                                                            .pref_player_media_segments_auto_skip_mode,
                                                    descriptionStringRes =
                                                        R.string
                                                            .pref_player_media_segments_auto_skip_mode_summary,
                                                    iconDrawableId = R.drawable.ic_settings_2,
                                                    dependencies =
                                                        listOf(
                                                            appPreferences
                                                                .playerMediaSegmentsAutoSkip
                                                        ),
                                                    supportedDeviceTypes = listOf(DeviceType.PHONE),
                                                    backendPreference =
                                                        appPreferences
                                                            .playerMediaSegmentsAutoSkipMode,
                                                    options = R.array.media_segments_auto_skip,
                                                    optionValues =
                                                        R.array.media_segments_auto_skip_values,
                                                ),
                                                PreferenceMultiSelect(
                                                    nameStringResource =
                                                        R.string
                                                            .pref_player_media_segments_auto_skip_type,
                                                    descriptionStringRes =
                                                        R.string
                                                            .pref_player_media_segments_auto_skip_type_summary,
                                                    iconDrawableId = R.drawable.ic_list,
                                                    dependencies =
                                                        listOf(
                                                            appPreferences
                                                                .playerMediaSegmentsAutoSkip
                                                        ),
                                                    backendPreference =
                                                        appPreferences
                                                            .playerMediaSegmentsAutoSkipType,
                                                    options = R.array.media_segments_type,
                                                    optionValues =
                                                        R.array.media_segments_type_values,
                                                ),
                                                PreferenceLongInput(
                                                    nameStringResource =
                                                        R.string
                                                            .pref_player_media_segments_next_episode_threshold,
                                                    descriptionStringRes =
                                                        R.string
                                                            .pref_player_media_segments_next_episode_threshold_summary,
                                                    iconDrawableId = R.drawable.ic_timer,
                                                    backendPreference =
                                                        appPreferences
                                                            .playerMediaSegmentsNextEpisodeThreshold,
                                                    suffixRes = R.string.ms,
                                                ),
                                            ),
                                    ),
                                    PreferenceGroup(
                                        nameStringResource = R.string.trickplay,
                                        preferences =
                                            listOf(
                                                PreferenceSwitch(
                                                    nameStringResource =
                                                        R.string.pref_player_trickplay,
                                                    descriptionStringRes =
                                                        R.string.pref_player_trickplay_summary,
                                                    iconDrawableId = R.drawable.ic_image,
                                                    backendPreference =
                                                        appPreferences.playerTrickplay,
                                                ),
                                                PreferenceSwitch(
                                                    nameStringResource =
                                                        R.string
                                                            .pref_player_gestures_seek_trickplay,
                                                    descriptionStringRes =
                                                        R.string
                                                            .pref_player_gestures_seek_trickplay_summary,
                                                    iconDrawableId =
                                                        R.drawable.ic_move_horizontal,
                                                    dependencies =
                                                        listOf(appPreferences.playerTrickplay),
                                                    supportedDeviceTypes = listOf(DeviceType.PHONE),
                                                    backendPreference =
                                                        appPreferences.playerGesturesSeekTrickplay,
                                                ),
                                            ),
                                    ),
                                    PreferenceGroup(
                                        nameStringResource = R.string.picture_in_picture,
                                        preferences =
                                            listOf(
                                                PreferenceSwitch(
                                                    nameStringResource =
                                                        R.string.picture_in_picture_gesture,
                                                    descriptionStringRes =
                                                        R.string.picture_in_picture_gesture_summary,
                                                    iconDrawableId =
                                                        R.drawable.ic_picture_in_picture,
                                                    supportedDeviceTypes = listOf(DeviceType.PHONE),
                                                    backendPreference =
                                                        appPreferences.playerPipGesture,
                                                )
                                            ),
                                    ),
                                ),
                        )
                    )
            ),
            PreferenceGroup(
                preferences =
                    listOf(
                        PreferenceCategory(
                            nameStringResource = R.string.settings_category_connections,
                            descriptionStringRes = R.string.settings_connections_summary,
                            iconDrawableId = R.drawable.ic_network,
                            supportedDeviceTypes = listOf(DeviceType.PHONE),
                            onClick = {
                                viewModelScope.launch {
                                    eventsChannel.send(SettingsEvent.NavigateToConnections)
                                }
                            },
                        ),
                        PreferenceCategory(
                            nameStringResource = R.string.settings_category_servers,
                            descriptionStringRes = R.string.settings_category_servers_summary,
                            iconDrawableId = R.drawable.ic_server,
                            supportedDeviceTypes = listOf(DeviceType.TV),
                            onClick = {
                                viewModelScope.launch {
                                    eventsChannel.send(SettingsEvent.NavigateToServers)
                                }
                            },
                        ),
                        PreferenceCategory(
                            nameStringResource = R.string.users,
                            descriptionStringRes = R.string.users_summary,
                            iconDrawableId = R.drawable.ic_user,
                            supportedDeviceTypes = listOf(DeviceType.TV),
                            onClick = {
                                viewModelScope.launch {
                                    eventsChannel.send(SettingsEvent.NavigateToUsers)
                                }
                            },
                        ),
                    ),
            ),
            PreferenceGroup(
                preferences =
                    listOf(
                        PreferenceCategory(
                            nameStringResource = R.string.settings_category_backup,
                            descriptionStringRes = R.string.settings_category_backup_summary,
                            iconDrawableId = R.drawable.ic_save,
                            supportedDeviceTypes = listOf(DeviceType.PHONE),
                            onClick = {
                                viewModelScope.launch {
                                    eventsChannel.send(SettingsEvent.NavigateToBackupSettings)
                                }
                            },
                        )
                    )
            ),
            PreferenceGroup(
                preferences =
                    listOf(
                        PreferenceCategory(
                            nameStringResource = R.string.title_download,
                            descriptionStringRes = R.string.title_download_summary,
                            iconDrawableId = R.drawable.ic_download,
                            supportedDeviceTypes = listOf(DeviceType.PHONE),
                            onClick = {
                                viewModelScope.launch {
                                    eventsChannel.send(
                                        SettingsEvent.NavigateToSettings(
                                            intArrayOf(it.nameStringResource)
                                        )
                                    )
                                }
                            },
                            nestedPreferenceGroups =
                                listOf(
                                    PreferenceGroup(
                                        nameStringResource = R.string.download_group_network,
                                        preferences =
                                            listOf(
                                                PreferenceSwitch(
                                                    nameStringResource =
                                                        R.string.download_mobile_data,
                                                    descriptionStringRes =
                                                        R.string.download_mobile_data_summary,
                                                    iconDrawableId = R.drawable.ic_network,
                                                    enabled = hasCellularConnectivity(),
                                                    supportedDeviceTypes = listOf(DeviceType.PHONE),
                                                    backendPreference =
                                                        appPreferences.downloadOverMobileData,
                                                ),
                                                PreferenceSwitch(
                                                    nameStringResource = R.string.download_roaming,
                                                    descriptionStringRes =
                                                        R.string.download_roaming_summary,
                                                    iconDrawableId = R.drawable.ic_globe,
                                                    enabled = hasCellularConnectivity(),
                                                    dependencies =
                                                        listOf(
                                                            appPreferences.downloadOverMobileData
                                                        ),
                                                    supportedDeviceTypes = listOf(DeviceType.PHONE),
                                                    backendPreference =
                                                        appPreferences.downloadWhenRoaming,
                                                ),
                                            ),
                                    ),
                                    PreferenceGroup(
                                        nameStringResource = R.string.download_group_storage,
                                        preferences =
                                            listOf(
                                                PreferenceSelect(
                                                    nameStringResource = R.string.download_location,
                                                    descriptionStringRes =
                                                        R.string.download_location_summary,
                                                    iconDrawableId = R.drawable.ic_hard_drive,
                                                    enabled = hasRemovableStorage(),
                                                    supportedDeviceTypes = listOf(DeviceType.PHONE),
                                                    backendPreference =
                                                        appPreferences.downloadLocation,
                                                    options = R.array.download_locations,
                                                    optionValues = R.array.download_locations_values,
                                                ),
                                                PreferenceIntInput(
                                                    nameStringResource =
                                                        R.string.max_parallel_downloads,
                                                    descriptionStringRes =
                                                        R.string.max_parallel_downloads_summary,
                                                    iconDrawableId = R.drawable.ic_gauge,
                                                    supportedDeviceTypes = listOf(DeviceType.PHONE),
                                                    backendPreference =
                                                        appPreferences.maxParallelDownloads,
                                                ),
                                                PreferenceSwitch(
                                                    nameStringResource =
                                                        R.string.download_pause_on_battery_saver,
                                                    descriptionStringRes =
                                                        R.string
                                                            .download_pause_on_battery_saver_summary,
                                                    iconDrawableId = R.drawable.ic_battery,
                                                    supportedDeviceTypes = listOf(DeviceType.PHONE),
                                                    backendPreference =
                                                        appPreferences.pauseDownloadsOnBatterySaver,
                                                ),
                                            ),
                                    ),
                                    PreferenceGroup(
                                        nameStringResource = R.string.download_group_auto_delete,
                                        preferences =
                                            listOf(
                                                PreferenceSwitch(
                                                    nameStringResource =
                                                        R.string.auto_delete_watched,
                                                    descriptionStringRes =
                                                        R.string.auto_delete_watched_summary,
                                                    iconDrawableId = R.drawable.ic_trash,
                                                    supportedDeviceTypes = listOf(DeviceType.PHONE),
                                                    backendPreference =
                                                        appPreferences.autoDeleteWatched,
                                                ),
                                                PreferenceIntInput(
                                                    nameStringResource =
                                                        R.string.auto_delete_watched_hours,
                                                    descriptionStringRes =
                                                        R.string.auto_delete_watched_hours_summary,
                                                    iconDrawableId = R.drawable.ic_timer,
                                                    dependencies =
                                                        listOf(appPreferences.autoDeleteWatched),
                                                    supportedDeviceTypes = listOf(DeviceType.PHONE),
                                                    backendPreference =
                                                        appPreferences.autoDeleteWatchedHours,
                                                    suffixRes = R.string.hours_suffix,
                                                ),
                                            ),
                                    ),
                                    PreferenceGroup(
                                        nameStringResource = R.string.download_group_auto_download,
                                        preferences =
                                            listOf(
                                                PreferenceIntInput(
                                                    nameStringResource =
                                                        R.string.auto_download_check_interval,
                                                    descriptionStringRes =
                                                        R.string.auto_download_check_interval_summary,
                                                    iconDrawableId = R.drawable.ic_refresh_cw,
                                                    supportedDeviceTypes = listOf(DeviceType.PHONE),
                                                    backendPreference =
                                                        appPreferences.autoDownloadCheckIntervalMinutes,
                                                    suffixRes = R.string.minutes_suffix,
                                                    presetsMinutes =
                                                        listOf(15, 30, 60, 120, 240, 720, 1440),
                                                    validRange = 15..(24 * 60),
                                                ),
                                                PreferenceCategory(
                                                    nameStringResource =
                                                        R.string.auto_download_rules,
                                                    descriptionStringRes =
                                                        R.string.auto_download_rules_summary,
                                                    iconDrawableId = R.drawable.ic_download,
                                                    supportedDeviceTypes = listOf(DeviceType.PHONE),
                                                    onClick = {
                                                        viewModelScope.launch {
                                                            eventsChannel.send(
                                                                SettingsEvent
                                                                    .NavigateToAutoDownloadRules
                                                            )
                                                        }
                                                    },
                                                ),
                                            ),
                                    ),
                                    PreferenceGroup(
                                        nameStringResource =
                                            R.string.download_group_new_item_notifications,
                                        preferences =
                                            listOf(
                                                PreferenceSwitch(
                                                    nameStringResource =
                                                        R.string.new_item_notifications_enabled,
                                                    descriptionStringRes =
                                                        R.string
                                                            .new_item_notifications_enabled_summary,
                                                    iconDrawableId = R.drawable.ic_bell,
                                                    supportedDeviceTypes = listOf(DeviceType.PHONE),
                                                    backendPreference =
                                                        appPreferences.newItemNotificationsEnabled,
                                                ),
                                                PreferenceIntInput(
                                                    nameStringResource =
                                                        R.string
                                                            .new_item_notifications_check_interval,
                                                    descriptionStringRes =
                                                        R.string
                                                            .new_item_notifications_check_interval_summary,
                                                    iconDrawableId = R.drawable.ic_refresh_cw,
                                                    dependencies =
                                                        listOf(
                                                            appPreferences
                                                                .newItemNotificationsEnabled
                                                        ),
                                                    supportedDeviceTypes = listOf(DeviceType.PHONE),
                                                    backendPreference =
                                                        appPreferences
                                                            .newItemNotificationsCheckIntervalMinutes,
                                                    suffixRes = R.string.minutes_suffix,
                                                    presetsMinutes =
                                                        listOf(15, 30, 60, 120, 240, 720, 1440),
                                                    validRange = 15..(24 * 60),
                                                ),
                                            ),
                                    ),
                                ),
                        )
                    )
            ),
            PreferenceGroup(
                preferences =
                    listOf(
                        PreferenceCategory(
                            nameStringResource = R.string.settings_category_network,
                            descriptionStringRes = R.string.settings_category_network_summary,
                            iconDrawableId = R.drawable.ic_network,
                            onClick = {
                                viewModelScope.launch {
                                    eventsChannel.send(
                                        SettingsEvent.NavigateToSettings(
                                            intArrayOf(it.nameStringResource)
                                        )
                                    )
                                }
                            },
                            nestedPreferenceGroups =
                                listOf(
                                    PreferenceGroup(
                                        preferences =
                                            listOf(
                                                PreferenceLongInput(
                                                    nameStringResource =
                                                        R.string.settings_request_timeout,
                                                    descriptionStringRes =
                                                        R.string.settings_request_timeout_summary,
                                                    iconDrawableId = R.drawable.ic_arrow_down_up,
                                                    backendPreference =
                                                        appPreferences.requestTimeout,
                                                    suffixRes = R.string.ms,
                                                ),
                                                PreferenceLongInput(
                                                    nameStringResource =
                                                        R.string.settings_connect_timeout,
                                                    descriptionStringRes =
                                                        R.string.settings_connect_timeout_summary,
                                                    iconDrawableId = R.drawable.ic_plug,
                                                    backendPreference =
                                                        appPreferences.connectTimeout,
                                                    suffixRes = R.string.ms,
                                                ),
                                                PreferenceLongInput(
                                                    nameStringResource =
                                                        R.string.settings_socket_timeout,
                                                    descriptionStringRes =
                                                        R.string.settings_socket_timeout_summary,
                                                    iconDrawableId = R.drawable.ic_timer,
                                                    backendPreference =
                                                        appPreferences.socketTimeout,
                                                    suffixRes = R.string.ms,
                                                ),
                                                PreferenceLongInput(
                                                    nameStringResource =
                                                        R.string.settings_pvr_search_timeout,
                                                    descriptionStringRes =
                                                        R.string.settings_pvr_search_timeout_summary,
                                                    iconDrawableId = R.drawable.ic_search,
                                                    backendPreference =
                                                        appPreferences.pvrSearchTimeout,
                                                    suffixRes = R.string.ms,
                                                ),
                                            )
                                    )
                                ),
                        )
                    )
            ),
            PreferenceGroup(
                preferences =
                    listOf(
                        PreferenceCategory(
                            nameStringResource = R.string.settings_category_cache,
                            descriptionStringRes = R.string.settings_category_cache_summary,
                            iconDrawableId = R.drawable.ic_hard_drive,
                            onClick = {
                                viewModelScope.launch {
                                    eventsChannel.send(
                                        SettingsEvent.NavigateToSettings(
                                            intArrayOf(it.nameStringResource)
                                        )
                                    )
                                }
                            },
                            nestedPreferenceGroups =
                                listOf(
                                    PreferenceGroup(
                                        preferences =
                                            listOf(
                                                PreferenceSwitch(
                                                    nameStringResource =
                                                        R.string.settings_use_cache_title,
                                                    descriptionStringRes =
                                                        R.string.settings_use_cache_summary,
                                                    iconDrawableId = R.drawable.ic_database,
                                                    backendPreference = appPreferences.imageCache,
                                                ),
                                                PreferenceIntInput(
                                                    nameStringResource =
                                                        R.string.settings_cache_size,
                                                    descriptionStringRes =
                                                        R.string.settings_cache_size_message,
                                                    iconDrawableId = R.drawable.ic_hard_drive,
                                                    dependencies =
                                                        listOf(appPreferences.imageCache),
                                                    backendPreference =
                                                        appPreferences.imageCacheSize,
                                                    suffixRes = R.string.mb,
                                                ),
                                            )
                                    )
                                ),
                        )
                    )
            ),
            PreferenceGroup(
                preferences =
                    listOf(
                        PreferenceCategory(
                            nameStringResource = R.string.about,
                            descriptionStringRes = R.string.about_summary,
                            iconDrawableId = R.drawable.ic_info,
                            onClick = {
                                viewModelScope.launch {
                                    eventsChannel.send(SettingsEvent.NavigateToAbout)
                                }
                            },
                        )
                    )
            ),
        )

    fun loadPreferences(indexes: IntArray = intArrayOf(), deviceType: DeviceType) {
        viewModelScope.launch {
            var preferences = topLevelPreferences

            // Tracks the icon of the deepest matched category, so the sub-screen header can show
            // the same icon as its row on the parent list (null while still at the root).
            var titleIcon: Int? = null

            // Show preferences based on the name of the parent
            for (index in indexes) {
                // If index is root (Settings) don't search for category - just skip straight to
                // the next index instead of stopping the whole drill-down here.
                if (index == R.string.title_settings) {
                    continue
                }
                val preference =
                    preferences
                        .flatMap { it.preferences }
                        .filterIsInstance<PreferenceCategory>()
                        .find { it.nameStringResource == index }
                if (preference != null) {
                    preferences = preference.nestedPreferenceGroups
                    titleIcon = preference.iconDrawableId
                }
            }

            // Update all (visible) preferences with there current values
            preferences =
                preferences
                    .map { preferenceGroup ->
                        preferenceGroup.copy(
                            preferences =
                                preferenceGroup.preferences
                                    .filter { it.supportedDeviceTypes.contains(deviceType) }
                                    .map { preference ->
                                        when (preference) {
                                            is PreferenceSwitch -> {
                                                preference.copy(
                                                    enabled =
                                                        preference.enabled &&
                                                            preference.dependencies.all {
                                                                appPreferences.getValue(it)
                                                            },
                                                    value =
                                                        appPreferences.getValue(
                                                            preference.backendPreference
                                                        ),
                                                )
                                            }
                                            is PreferenceSelect -> {
                                                preference.copy(
                                                    enabled =
                                                        preference.enabled &&
                                                            preference.dependencies.all {
                                                                appPreferences.getValue(it)
                                                            },
                                                    value =
                                                        appPreferences.getValue(
                                                            preference.backendPreference
                                                        ),
                                                )
                                            }
                                            is PreferenceMultiSelect -> {
                                                preference.copy(
                                                    enabled =
                                                        preference.enabled &&
                                                            preference.dependencies.all {
                                                                appPreferences.getValue(it)
                                                            },
                                                    value =
                                                        appPreferences.getValue(
                                                            preference.backendPreference
                                                        ),
                                                )
                                            }
                                            is PreferenceIntInput -> {
                                                preference.copy(
                                                    enabled =
                                                        preference.enabled &&
                                                            preference.dependencies.all {
                                                                appPreferences.getValue(it)
                                                            },
                                                    value =
                                                        appPreferences.getValue(
                                                            preference.backendPreference
                                                        ),
                                                )
                                            }
                                            is PreferenceLongInput -> {
                                                preference.copy(
                                                    enabled =
                                                        preference.enabled &&
                                                            preference.dependencies.all {
                                                                appPreferences.getValue(it)
                                                            },
                                                    value =
                                                        appPreferences.getValue(
                                                            preference.backendPreference
                                                        ),
                                                )
                                            }
                                            else -> preference
                                        }
                                    }
                        )
                    }
                    .filter { it.preferences.isNotEmpty() }

            _state.emit(
                _state.value.copy(preferenceGroups = preferences, titleIconDrawableId = titleIcon)
            )
        }
    }

    fun onAction(action: SettingsAction) {
        when (action) {
            is SettingsAction.OnUpdate -> {
                when (action.preference) {
                    is PreferenceSwitch ->
                        appPreferences.setValue(
                            action.preference.backendPreference,
                            action.preference.value,
                        )
                    is PreferenceSelect -> {
                        val backendPreference = action.preference.backendPreference
                        val newValue = action.preference.value
                        if (backendPreference == appPreferences.downloadLocation) {
                            val oldValue = appPreferences.getValue(backendPreference)
                            appPreferences.setValue(backendPreference, newValue)
                            // Only concrete internal<->external switches have an old/new location
                            // to act on - "ask" has no fixed volume, so there's nothing to move
                            // from/to and no prompt is shown for changes involving it.
                            if (
                                oldValue != newValue &&
                                    oldValue in CONCRETE_DOWNLOAD_LOCATIONS &&
                                    newValue in CONCRETE_DOWNLOAD_LOCATIONS
                            ) {
                                viewModelScope.launch {
                                    eventsChannel.send(
                                        SettingsEvent.DownloadLocationChanged(
                                            oldValue!!,
                                            newValue!!,
                                        )
                                    )
                                }
                            }
                        } else {
                            appPreferences.setValue(backendPreference, newValue)
                        }
                    }
                    is PreferenceMultiSelect ->
                        appPreferences.setValue(
                            action.preference.backendPreference,
                            action.preference.value,
                        )
                    is PreferenceIntInput ->
                        appPreferences.setValue(
                            action.preference.backendPreference,
                            action.preference.value,
                        )
                    is PreferenceLongInput ->
                        appPreferences.setValue(
                            action.preference.backendPreference,
                            action.preference.value,
                        )
                }
            }
            else -> Unit
        }
    }
}
