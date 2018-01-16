package nikita.com.cameraapp.main;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.EditText;
import nikita.com.cameraapp.R;
import nikita.com.cameraapp.dataacquirer.CameraDataAcquirer;

import java.util.ArrayList;

import static android.provider.AlarmClock.EXTRA_MESSAGE;

public class LoginActivity extends AppCompatActivity
{
	private final String TAG = "CAMERA_SENSOR_ACQUIRER";

	private final String[] permissions = new String[] { Manifest.permission.CAMERA, Manifest.permission.INTERNET };

	public LoginActivity( )
	{
		super( );
	}

	@Override
	protected void onCreate( Bundle savedInstanceState )
	{
		super.onCreate( savedInstanceState );

		checkPermissions( );

		setContentView( R.layout.activity_login );
	}

	private void checkPermissions( )
	{
		ArrayList< String > neededPermissions = new ArrayList<>( );
		for ( String permission : permissions )
		{
			if ( checkSelfPermission( permission ) != PackageManager.PERMISSION_GRANTED )
			{
				neededPermissions.add( permission );
			}
		}

		if ( !neededPermissions.isEmpty( ) )
		{
			int requestCode = 0;
			String[] permissionArray = new String[ neededPermissions.size( ) ];
			neededPermissions.toArray( permissionArray );
			requestPermissions( permissionArray, requestCode );
		}
		else
		{
			start( );
		}
	}

	@Override
	public void onRequestPermissionsResult( int requestCode,
											@NonNull String[] permissions,
											@NonNull int[] grantResults)
	{
		for ( int permissionIndex = 0; permissionIndex < permissions.length; permissionIndex += 1 )
		{
			if ( grantResults[ permissionIndex ] != PackageManager.PERMISSION_GRANTED )
			{
				Log.e( TAG, "Permission " + permissions[ permissionIndex ] + " not given, quit" );

				finishAffinity( );
			}
		}

		start( );
	}

	private void start( )
	{
		Intent intent = new Intent(this, MainActivity.class);
		startActivity( intent );
	}
}
