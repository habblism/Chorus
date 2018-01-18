package yhb.chorus.main;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.provider.MediaStore;
import android.util.Log;

import java.lang.reflect.Field;
import java.util.ArrayList;

import yhb.chorus.db.MP3DBHelper;
import yhb.chorus.db.MP3DbSchema.MP3Table;
import yhb.chorus.entity.MP3;
import yhb.chorus.list.ListContract;
import yhb.chorus.service.PlayCenter;

import static yhb.chorus.db.MP3DbSchema.MP3Table.Cols.ALBUM;
import static yhb.chorus.db.MP3DbSchema.MP3Table.Cols.ALBUM_ID;
import static yhb.chorus.db.MP3DbSchema.MP3Table.Cols.ARTIST;
import static yhb.chorus.db.MP3DbSchema.MP3Table.Cols.DURATION;
import static yhb.chorus.db.MP3DbSchema.MP3Table.Cols.IS_MUSIC;
import static yhb.chorus.db.MP3DbSchema.MP3Table.Cols.SIZE;
import static yhb.chorus.db.MP3DbSchema.MP3Table.Cols.TITLE;
import static yhb.chorus.db.MP3DbSchema.MP3Table.Cols.URI;
import static yhb.chorus.list.ListActivity.TAG;

/**
 * Created by yhb on 18-1-17.
 */

class MainPresenter implements MainContract.Presenter {
    private Context mContext;
    private MainContract.View mView;
    private PlayCenter mPlayCenter;
    private SQLiteDatabase mDatabase;

    MainPresenter(Context context, MainContract.View view) {
        mContext = context;
        mView = view;
        mView.setPresenter(this);
        mDatabase = new MP3DBHelper(context).getWritableDatabase();
        mPlayCenter = PlayCenter.getInstance(context);
    }

    @Override
    public void start() {
        ArrayList<MP3> mp3s = query(MP3Table.NAME, MP3.class, null, null);
        mPlayCenter.setMp3s(mp3s);
    }


    private  <T> ArrayList<T> query(String tableName, Class<T> entityType, String fieldName, String value) {

        ArrayList<T> list = new ArrayList<>();
        Cursor cursor;
        if (fieldName == null) {
            cursor = mDatabase.query(tableName, null, null, null, null, null, " id desc", null);
        } else {
            cursor = mDatabase.query(tableName, null, fieldName + " like ?", new String[]{value}, null, null, " id desc", null);
        }
        cursor.moveToFirst();
        while (!cursor.isAfterLast()) {
            try {
                T t = entityType.newInstance();
                for (int i = 0; i < cursor.getColumnCount(); i++) {
                    Object content = null;
                    String columnName = cursor.getColumnName(i);// 获取数据记录第i条字段名的
                    if (columnName.equals("_id")) {
                        columnName = columnName.replace("_","" );
                    }
                    switch (columnName) {
                        case MP3Table.Cols.ID:
                        case MP3Table.Cols.ALBUM_ID:
                        case MP3Table.Cols.SIZE:
                            content = cursor.getLong(cursor.getColumnIndex(columnName));
                            break;
                        case MP3Table.Cols.TITLE:
                        case MP3Table.Cols.ARTIST:
                        case MP3Table.Cols.URI:
                        case MP3Table.Cols.ALBUM:
                            content= cursor.getString(cursor.getColumnIndex(columnName));
                            break;
                        case MP3Table.Cols.IS_MUSIC:
                        case MP3Table.Cols.DURATION:
                            content = cursor.getInt(cursor.getColumnIndex(columnName));
                            break;
                    }

                    Field field = entityType.getDeclaredField(columnName);//获取该字段名的Field对象。
                    field.setAccessible(true);//取消对age属性的修饰符的检查访问，以便为属性赋值
                    field.set(t, content);
                    field.setAccessible(false);//恢复对age属性的修饰符的检查访问
                }
                list.add(t);
                cursor.moveToNext();
            } catch (InstantiationException | IllegalAccessException | NoSuchFieldException e) {
                e.printStackTrace();
            }
        }

        cursor.close();
        return list;
    }


}
