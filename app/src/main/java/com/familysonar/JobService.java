package com.familysonar;

import android.Manifest;
import android.app.job.JobParameters;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.HandlerThread;
import android.telephony.CellInfo;
import android.telephony.CellInfoCdma;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoTdscdma;
import android.telephony.CellInfoWcdma;
import android.telephony.CellSignalStrengthGsm;
import android.telephony.CellSignalStrengthWcdma;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import java.util.ArrayList;

public class JobService extends android.app.job.JobService {
    private static String TAG = "JobService";
    ConfigData _config = null;

    @Override
    public boolean onStartJob(JobParameters jobParameters) {
        String message = jobParameters.getExtras().getString("message");
        String from = jobParameters.getExtras().getString("from");
        Log.d("FalimySonarApp", "onStartJob");
        if (message.compareTo("?loc?")==0) {
            TelephonyManager telephonyManager = (TelephonyManager) this.getSystemService(TELEPHONY_SERVICE);

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                for (CellInfo cellInfo : telephonyManager.getAllCellInfo()) {
                    String gsmLoc = null;
                    if (cellInfo instanceof CellInfoGsm) {
                        CellInfoGsm cellInfogsm = (CellInfoGsm) cellInfo;
                        gsmLoc = String.format("[T:GSM,MCC:%s,MNC:%s,LAC:%d,CID:%d,dBm:%d]",
                                cellInfogsm.getCellIdentity().getMccString(),
                                cellInfogsm.getCellIdentity().getMncString(),
                                cellInfogsm.getCellIdentity().getLac(),
                                cellInfogsm.getCellIdentity().getCid(),
                                cellInfogsm.getCellSignalStrength().getDbm()

                        );

                    } else if (cellInfo instanceof CellInfoCdma) {
                        CellInfoCdma cellInfoTemp = (CellInfoCdma) cellInfo;
                        gsmLoc = String.format("[T:CDMA, LAT:%d,LONG:%d,NETID:%d,BASEID:%d,dBm:%d]",
                                cellInfoTemp.getCellIdentity().getLatitude(),
                                cellInfoTemp.getCellIdentity().getLongitude(),
                                cellInfoTemp.getCellIdentity().getNetworkId(),
                                cellInfoTemp.getCellIdentity().getBasestationId(),
                                cellInfoTemp.getCellSignalStrength().getDbm());

                    } else if (cellInfo instanceof CellInfoLte) {
                        CellInfoLte cellInfoTemp = (CellInfoLte) cellInfo;
                        gsmLoc = String.format("[T:LTE,MCC:%s,MNC:%s,TAC:%d,CID:%d,dBm:%d]",
                                cellInfoTemp.getCellIdentity().getMncString(),
                                cellInfoTemp.getCellIdentity().getMncString(),
                                cellInfoTemp.getCellIdentity().getTac(),
                                cellInfoTemp.getCellIdentity().getCi(),
                                cellInfoTemp.getCellSignalStrength().getDbm()
                        );

                    } else if (cellInfo instanceof CellInfoWcdma) {
                        CellInfoWcdma cellInfoTemp = (CellInfoWcdma) cellInfo;
                        gsmLoc = String.format("[T:WCDMA,MCC:%s,MNC:%s,TAC:%d,CID:%d,dBm:%d]",
                                cellInfoTemp.getCellIdentity().getMccString(),
                                cellInfoTemp.getCellIdentity().getMncString(),
                                cellInfoTemp.getCellIdentity().getLac(),
                                cellInfoTemp.getCellIdentity().getCid(),
                                cellInfoTemp.getCellSignalStrength().getDbm()
                        );

                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        if (cellInfo instanceof CellInfoTdscdma) {
                            CellInfoTdscdma cellInfoTemp = (CellInfoTdscdma) cellInfo;
                            gsmLoc = String.format("[T:TDSCDMA,MCC:%s,MNC:%s,TAC:%d,CID:%d,dBm:%d]",
                                    cellInfoTemp.getCellIdentity().getMccString(),
                                    cellInfoTemp.getCellIdentity().getMncString(),
                                    cellInfoTemp.getCellIdentity().getLac(),
                                    cellInfoTemp.getCellIdentity().getCid(),
                                    cellInfoTemp.getCellSignalStrength().getDbm()
                            );

                        }
                    }
                    if (gsmLoc != null){
                        SmsManager.getDefault().sendTextMessage(from, null, gsmLoc, null, null);
                    }

                }

            }
        }
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters jobParameters) {
        return false;
    }
}
