package com.urbantechies.fetch_me_up.drivers;


import android.os.Bundle;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.urbantechies.fetch_me_up.R;


/**
 * A simple {@link Fragment} subclass.
 */
public class TripDetailFragment extends Fragment {


    public TripDetailFragment() {
        // Required empty public constructor
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_trip_detail, container, false);
    }

}
