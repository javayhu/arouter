package com.github.mzule.activityrouter.router;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

/**
 * Created by CaoDongping on 4/6/16.
 */
public class Routers {

    public static String KEY_RAW_URL = "arouter.KeyRawUrl";

    private static List<Mapping> mappings = new ArrayList<>();

    private static void initIfNeed() {
        if (!mappings.isEmpty()) {
            return;
        }
        RouterInit.init();
        sort();
    }

    static void map(String format, Class<? extends Activity> activity, MethodInvoker method, ExtraTypes extraTypes) {
        mappings.add(new Mapping(format, activity, method, extraTypes));
    }

    private static void sort() {
        // ensure that user/collection is rank top over user/:userId
        // so scheme://user/collection will match user/collection not user/:userId
        Collections.sort(mappings, new Comparator<Mapping>() {
            @Override
            public int compare(Mapping lhs, Mapping rhs) {
                return lhs.getFormat().compareTo(rhs.getFormat()) * -1;
            }
        });
    }

    //these first three methods will call the next three methods, at last the last method will be called
    public static boolean open(Context context, String url) {
        return open(context, Uri.parse(url));
    }

    public static boolean open(Context context, String url, Bundle options) {
        return open(context, Uri.parse(url), options);
    }

    public static boolean open(Context context, String url, Bundle options, RouterCallback callback) {
        return open(context, Uri.parse(url), options, callback);
    }

    public static boolean open(Context context, Uri uri) {
        return open(context, uri, null, getGlobalCallback(context));
    }

    public static boolean open(Context context, Uri uri, Bundle options) {
        return open(context, uri, options, getGlobalCallback(context));
    }

    public static boolean open(Context context, Uri uri, Bundle options, RouterCallback callback) {
        return open(context, uri, options, -1, callback);
    }

    //these first two methods will call the next two methods, at last the last method will be called
    public static boolean openForResult(Activity activity, String url, int requestCode) {
        return openForResult(activity, Uri.parse(url), requestCode);
    }

    public static boolean openForResult(Activity activity, String url, Bundle options, int requestCode) {
        return openForResult(activity, Uri.parse(url), options, requestCode);
    }

    public static boolean openForResult(Activity activity, String url, Bundle options, int requestCode, RouterCallback callback) {
        return openForResult(activity, Uri.parse(url), options, requestCode, callback);
    }

    public static boolean openForResult(Activity activity, Uri uri, int requestCode) {
        return openForResult(activity, uri, null, requestCode, getGlobalCallback(activity));
    }

    public static boolean openForResult(Activity activity, Uri uri, Bundle options, int requestCode) {
        return openForResult(activity, uri, options, requestCode, getGlobalCallback(activity));
    }

    public static boolean openForResult(Activity activity, Uri uri, Bundle options, int requestCode, RouterCallback callback) {
        return open(activity, uri, options, requestCode, callback);
    }

    private static boolean open(Context context, Uri uri, Bundle options, int requestCode, RouterCallback callback) {
        boolean success = false;
        if (callback != null) {
            if (callback.beforeOpen(context, uri)) {
                return false;
            }
        }

        try {
            success = doOpen(context, uri, options, requestCode);
        } catch (Throwable e) {
            e.printStackTrace();
            if (callback != null) {
                callback.error(context, uri, e);
            }
        }

        if (callback != null) {
            if (success) {
                callback.afterOpen(context, uri);
            } else {
                callback.notFound(context, uri);
            }
        }
        return success;
    }

    public static Intent resolve(Context context, String url) {
        return resolve(context, Uri.parse(url));
    }

    public static Intent resolve(Context context, Uri uri) {
        initIfNeed();
        Path path = Path.create(uri);
        for (Mapping mapping : mappings) {
            if (mapping.match(path)) {
                Intent intent = new Intent(context, mapping.getActivity());
                intent.putExtras(mapping.parseExtras(uri));
                intent.putExtra(KEY_RAW_URL, uri.toString());
                return intent;
            }
        }
        return null;
    }

    private static boolean doOpen(Context context, Uri uri, Bundle options, int requestCode) {
        initIfNeed();
        Path path = Path.create(uri);
        for (Mapping mapping : mappings) {
            if (mapping.match(path)) {
                if (mapping.getActivity() == null) {
                    mapping.getMethod().invoke(context, mapping.parseExtras(uri));
                    return true;
                }
                Intent intent = new Intent(context, mapping.getActivity());
                intent.putExtras(mapping.parseExtras(uri));
                intent.putExtra(KEY_RAW_URL, uri.toString());
                if (!(context instanceof Activity)) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                }
                if (requestCode >= 0) {
                    if (context instanceof Activity) {
                        ((Activity) context).startActivityForResult(intent, requestCode);
                    } else {
                        throw new RuntimeException("can not startActivityForResult context " + context);
                    }
                } else {
                    if (options != null) {
                        context.startActivity(intent, options);
                    } else {
                        context.startActivity(intent);
                    }
                }
                return true;
            }
        }
        return false;
    }

    private static RouterCallback getGlobalCallback(Context context) {
        if (context.getApplicationContext() instanceof RouterCallbackProvider) {
            return ((RouterCallbackProvider) context.getApplicationContext()).provideRouterCallback();
        }
        return null;
    }
}
