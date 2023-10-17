package com.caburum.tasmotaqs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

internal val Context.dataStore by preferencesDataStore(name = "quicksettings")

/** Whether the tile has been added to the Quick Settings. */
internal val TILE_ADDED = booleanPreferencesKey("tile_added")

/** The tile is toggleable. This state represents whether it is currently active or not. */
internal val TILE_ACTIVE = booleanPreferencesKey("tile_active")

internal val ALLOWED_NETWORKS = stringSetPreferencesKey("allowed_networks")
internal val TASMOTA_IP = stringPreferencesKey("tasmota_ip")
internal val TASMOTA_PORT = stringPreferencesKey("tasmota_port")