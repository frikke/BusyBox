/*
 * Copyright (C) 2016 Jared Rummler <jared.rummler@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.jrummyapps.busybox.fragments;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.jrummyapps.android.preferences.fragments.AboutPreferenceFragment;

public class AboutFragment extends AboutPreferenceFragment {

  @Override public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setHasOptionsMenu(true);
  }

  @Override public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
    /*menu.add(0, R.id.action_view_profile, 0, R.string.profile)
        .setIcon(R.drawable.ic_account_box_white_24dp)
        .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);*/
    super.onCreateOptionsMenu(menu, inflater);
  }

  @Override public boolean onOptionsItemSelected(MenuItem item) {
    /*if (item.getItemId() == R.id.action_view_profile) {
      Radiant radiant = Radiant.getInstance(getActivity());
      Intent intent = new Intent(getActivity(), DeveloperProfileActivity.class);
      intent.putExtra(FabDialogMorphSetup.EXTRA_SHARED_ELEMENT_START_COLOR, radiant.primaryColor());
      intent.putExtra(FabDialogMorphSetup.EXTRA_SHARED_ELEMENT_START_CORNER_RADIUS, 0);
      if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) {
        View menuItemView = getActivity().findViewById(R.id.action_view_profile);
        ActivityOptions options = ActivityOptions.makeSceneTransitionAnimation(
            getActivity(), menuItemView, getString(R.string.morphing_dialog_transition));
        startActivity(intent, options.toBundle());
      } else {
        startActivity(intent);
      }
    }*/
    return super.onOptionsItemSelected(item);
  }

}
