#!/system/bin/sh
for package in com.android.hotwordenrollment.xgoogle com.android.hotwordenrollment.okgoogle; do
    am compat reset 255038118 "$package"
done
# User enrollment/model databases are intentionally not erased during uninstall.
