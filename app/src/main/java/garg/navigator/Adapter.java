package garg.navigator;

import android.content.Context;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * Created by Shivam Garg on 08-10-2018.
 */

public class Adapter extends ArrayAdapter<Model> {

    Adapter(@NonNull Context context, ArrayList<Model> resource) {
        super(context, 0, resource);
    }

    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        View listItemView = convertView;

        if (listItemView == null) {
            listItemView = LayoutInflater.from(getContext()).inflate(R.layout.list_item, parent, false);
        }

        Model current = getItem(position);

        TextView direction = (TextView) listItemView.findViewById(R.id.direction);
        direction.setText(current.getHtmlDirection());

        TextView time = (TextView) listItemView.findViewById(R.id.time);
        time.setText(current.getTime());

        ImageView image = (ImageView) listItemView.findViewById(R.id.icon);
        int id = getContext().getResources().getIdentifier("garg.navigator:drawable/" + current.getDirection().toString(), null, null);
        image.setImageResource(id);
        return listItemView;
    }
}
