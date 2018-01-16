package nikita.com.cameraapp.dataacquirer;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.camera2.*;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.util.Log;
import android.util.Pair;
import android.util.Range;
import android.util.Size;
import android.view.Surface;

import java.io.Closeable;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;


/**
 * Created by Nikita on 21.11.2017.
 */

public class CameraDataAcquirer
	implements Closeable
{
	/**
	 * Default and final values.
	 */
	private final String DEFAULT_CAMERA_ID = "0";
	private final String TAG = "CameraDataAcquirer";
	private final String CameraPermissionErrorString = "Camera permission not given";
	private final int MAX_IMAGES_IN_SESSION = 50;

	/**
	 * Fields related to camera and acquiring data from it.
	 */
	private CameraDevice cameraDevice = null;
	private CameraCaptureSession cameraCaptureSession = null;
	private CameraManager cameraManager;
	private CaptureRequest.Builder cameraCaptureRequestBuilder = null;

	private Integer sensorSensitivity = 0;
	private Long exposureTime = 0L;
	private int imageFormat = ImageFormat.YUV_420_888;// ImageFormat.RAW_SENSOR;

	/**
	 * Fields specifying this instance.
	 */
	private String cameraId;
	private Context context;

	/**
	 * Worker instances.
	 */
	private Handler cameraCaptureHandler = null;
	private HandlerThread cameraCaptureThread = null;



	private ConcurrentLinkedQueue< ImageReader > imageReaders = new ConcurrentLinkedQueue<>( );
	private ImageReader cameraImageReader;



	/**
	 * Handlers.
	 */
	public static class CaptureInfo
	{
		public final int imageFormat;
		public final Integer sensitivity;
		public final Long exposureTime;
		public final int imageWidth;
		public final int imageHeight;

		CaptureInfo( int imageFormat, Integer sensitivity, Long exposureTime, int imageWidth, int imageHeight )
		{
			this.imageFormat = imageFormat;
			this.sensitivity = sensitivity;
			this.exposureTime = exposureTime;
			this.imageWidth = imageWidth;
			this.imageHeight = imageHeight;
		}
	}

	public interface CameraDataReader
	{
		void ready( byte[] data, CaptureInfo captureInfo );
	}
	private CameraDataReader cameraDataReader = null;

	private Runnable onCameraOpenedRunnable = null;
	private Runnable onCameraDisconnectedRunnable = null;
	private CameraDevice.StateCallback cameraStateCallback = null;
	private CameraCaptureSession.StateCallback sessionStateCallback = null;
	public static abstract class CameraErrorHandler
	{
		public abstract void onError( @NonNull CameraDevice cameraDevice, int i );
	}
	private CameraErrorHandler onCameraErrorHandler = null;
	private Consumer< CameraCaptureSession > onCameraSessionConfigureFailed = null;
	private Runnable onCameraSessionConfiguredRunnable = null;

	/* PUBLIC METHODS */

	public CameraDataAcquirer( Context context )
			throws CameraAccessException
	{
		this.context = context;

		cameraManager = ( CameraManager )context.getSystemService( Context.CAMERA_SERVICE );

		initializeCameraStateCallback( );
		initializeSessionStateCallback( );

		try
		{
			setCameraId( DEFAULT_CAMERA_ID );
		}
		catch ( CameraAccessException e )
		{
			throw new CameraAccessException( e.getReason( ),
					"Default camera (id=" + DEFAULT_CAMERA_ID + ") cannot be selected: " + e.getMessage( ) );
		}
	}

	public void setCameraId( String cameraId )
			throws CameraAccessException
	{
		boolean cameraIdFound = false;
		for ( String id : cameraManager.getCameraIdList( ) )
		{
			if ( id.equals( cameraId ) )
			{
				cameraIdFound = true;
			}
		}

		if ( !cameraIdFound )
		{
			throw new CameraAccessException( CameraAccessException.CAMERA_ERROR,
					"Camera with id=\'" + cameraId + "\' is not available" );
		}

		this.cameraId = cameraId;

		resetSensorSettings( );
	}

	public String getCameraId( )
	{
		return cameraId;
	}

	public void setSensorSensitivity( Integer sensorSensitivity )
	{
		this.sensorSensitivity = sensorSensitivity;
	}

	public Range< Integer > getAvailableSensorSensitivity( )
			throws CameraAccessException
	{
		return getCameraCharacteristics( ).get( CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE );
	}

	public void setExposureTime( Long exposureTime )
	{
		this.exposureTime = exposureTime;
	}

	public void setImageFormat( int imageFormat )
			throws CameraAccessException
	{
		StreamConfigurationMap configurationMap = getCameraCharacteristics( ).get(
				CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP );
		if ( configurationMap == null )
		{
			throw new CameraAccessException( CameraAccessException.CAMERA_ERROR,
					"Cannot instantiate StreamConfigurationMap" );
		}

		Size[] imageSizes = configurationMap.getOutputSizes( imageFormat );
		if ( imageSizes == null || imageSizes.length == 0 )
		{
			throw new CameraAccessException( CameraAccessException.CAMERA_ERROR, "Image format not supported" );
		}

		this.imageFormat = imageFormat;
	}

	public Range< Long > getAvailableExposureTime( )
			throws CameraAccessException
	{
		return getCameraCharacteristics( ).get( CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE );
	}

	public void setOnCameraOpened( Runnable onCameraOpened )
	{
		this.onCameraOpenedRunnable = onCameraOpened;
	}

	public void setOnCameraDisconnected( Runnable onCameraDisconnected )
	{
		this.onCameraDisconnectedRunnable = onCameraDisconnected;
	}

	public void setOnCameraSessionConfigured( Runnable onCameraSessionConfigured )
	{
		this.onCameraSessionConfiguredRunnable = onCameraSessionConfigured;
	}

	public void setOnCameraSessionConfigureFailed( Consumer< CameraCaptureSession > onCameraSessionConfigureFailed )
	{
		this.onCameraSessionConfigureFailed = onCameraSessionConfigureFailed;
	}

	public void setCameraDataReader( CameraDataReader cameraDataReader )
	{
		this.cameraDataReader = cameraDataReader;
	}

	public void openCamera( )
			throws CameraAccessException
		{
			resetCameraSession( );

			if ( context.checkSelfPermission( Manifest.permission.CAMERA ) !=
					PackageManager.PERMISSION_GRANTED )
			{
				throw new CameraAccessException( CameraAccessException.CAMERA_ERROR, CameraPermissionErrorString );
			}

			cameraManager.openCamera( cameraId, cameraStateCallback, cameraCaptureHandler );
		}

	public CameraCharacteristics getCameraCharacteristics( )
			throws CameraAccessException
	{
		return cameraManager.getCameraCharacteristics( cameraId );
	}

	public Size getSensorSize( )
			throws CameraAccessException
	{
		StreamConfigurationMap configurationMap = getCameraCharacteristics( ).get(
				CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP );
		if ( configurationMap == null )
		{
			throw new CameraAccessException( CameraAccessException.CAMERA_ERROR,
					"Cannot instantiate StreamConfigurationMap" );
		}

		Size[] imageSizes = configurationMap.getOutputSizes( imageFormat );
		if ( imageSizes == null || imageSizes.length == 0 )
		{
			throw new CameraAccessException( CameraAccessException.CAMERA_ERROR, "Image format not supported" );
		}

		// Uncomment in case of using RAW im image format.
		/*
		if ( imageSizes.length > 1 )
		{
			throw new RuntimeException( "There are more than one available image sizes for this format, " +
					"possibly format is not set to RAW" );
		}*/

		// The first size is supposed to be the largest.
		return imageSizes[ 0 ];
	}

	public Size[] getAvailableSizes( )
			throws CameraAccessException
	{
		StreamConfigurationMap configurationMap = getCameraCharacteristics( ).get(
				CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP );
		if ( configurationMap == null )
		{
			throw new CameraAccessException( CameraAccessException.CAMERA_ERROR,
					"Cannot instantiate StreamConfigurationMap" );
		}

		Size[] imageSizes = configurationMap.getOutputSizes( imageFormat );
		if ( imageSizes == null || imageSizes.length == 0 )
		{
			throw new CameraAccessException( CameraAccessException.CAMERA_ERROR, "Image format not supported" );
		}

		return imageSizes;
	}

	public void initializeCaptureSession( )
			throws CameraAccessException
	{
		final CameraDataReader cameraDataReaderFinal = cameraDataReader;
		if ( cameraDataReaderFinal == null )
		{
			throw new NullPointerException( "CameraDataListener should not be null" );
		}

		Size sensorSize = getSensorSize( );
		cameraImageReader = ImageReader.newInstance(
				sensorSize.getWidth( ),
				sensorSize.getHeight( ),
				imageFormat,
				MAX_IMAGES_IN_SESSION );

		// Creating array of surfaces and adding one of our ImageReader
		List< Surface > outputSurfaces = new ArrayList<>( );
		outputSurfaces.add( cameraImageReader.getSurface( ) );

		// Creating manual template of capture request so that we can set capture parameters and binding it to ImageReader
		cameraCaptureRequestBuilder = cameraDevice.createCaptureRequest( CameraDevice.TEMPLATE_RECORD );
//		cameraCaptureRequestBuilder = cameraDevice.createCaptureRequest( CameraDevice.TEMPLATE_MANUAL );
		cameraCaptureRequestBuilder.addTarget( cameraImageReader.getSurface( ) );

		// Setting capture parameters
		cameraCaptureRequestBuilder.set( CaptureRequest.SENSOR_EXPOSURE_TIME, exposureTime );
		cameraCaptureRequestBuilder.set( CaptureRequest.SENSOR_SENSITIVITY, sensorSensitivity );

		// Setting listener of image available event
		ImageReader.OnImageAvailableListener imageAvailableListener = ( imageReader ) ->
		{
			new Thread( ( ) ->
					{
						imageReader.acquireLatestImage( ).close( );

						/////////////////
						/*
						imageReaders.add( imageReader );
						while ( imageReaders.size( ) > 16 )
						{
							imageReaders.poll( );
						}*/
						/////////////////
/*
						Image image = imageReader.acquireNextImage( );

						CaptureInfo captureInfo = new CaptureInfo(
								image.getFormat( ),
								sensorSensitivity,
								exposureTime,
								image.getWidth( ),
								image.getHeight( ) );

						int imageCapacity = 0; //metadataOffsetByteCount;
						for ( int planeIndex = 0; planeIndex < 1; planeIndex += 1 )
						{
							imageCapacity += image.getPlanes( )[ planeIndex ].getBuffer( ).capacity( );
						}

						byte[] data = new byte[ imageCapacity ];
						int firstByteIndex = 0; // metadataOffsetByteCount;
						for ( int planeIndex = 0; planeIndex < 1; planeIndex += 1 )
						{
							ByteBuffer byteBuffer = image.getPlanes( )[ planeIndex ].getBuffer( );
							byteBuffer.get( data, firstByteIndex, byteBuffer.capacity( ) );
							firstByteIndex += byteBuffer.capacity( );
						}

						image.close( );*/

						CaptureInfo captureInfo = new CaptureInfo( 0, 0, 0L, 0, 0 );
						byte[] data = new byte[]{ 1, 2, 3, 4, 5 };

						cameraDataReaderFinal.ready( data, captureInfo );
					}
			).start( );
		};
		cameraImageReader.setOnImageAvailableListener( imageAvailableListener, cameraCaptureHandler );

		// Creating capture session
		cameraDevice.createCaptureSession( outputSurfaces, sessionStateCallback, cameraCaptureHandler );
	}

	public void startRepeatingRequests( )
			throws CameraAccessException
	{
		cameraCaptureSession.setRepeatingRequest( cameraCaptureRequestBuilder.build( ),
				null,
				cameraCaptureHandler );
	}

	public void requestCapture( )
			throws CameraAccessException
	{
		cameraCaptureRequestBuilder.set( CaptureRequest.SENSOR_SENSITIVITY, sensorSensitivity );
		cameraCaptureRequestBuilder.set( CaptureRequest.SENSOR_EXPOSURE_TIME, exposureTime );

		cameraCaptureSession.capture( cameraCaptureRequestBuilder.build( ), new CameraCaptureSession.CaptureCallback( )
		{
			@Override
			public void onCaptureStarted( @NonNull CameraCaptureSession session, @NonNull CaptureRequest request, long timestamp, long frameNumber )
			{
				super.onCaptureStarted( session, request, timestamp, frameNumber );
			}
		}, cameraCaptureHandler );
	}

	public List< Pair< Integer, String > > getAvailableFormats( )
	{
		List< Pair< Integer, String > > formats = new ArrayList<>( );

		formats.add( new Pair<>( ImageFormat.RAW_SENSOR, "RAW" ) );
		formats.add( new Pair<>( ImageFormat.YUV_420_888, "YUV 420 888" ) );
		formats.add( new Pair<>( ImageFormat.JPEG, "JPEG" ) );

		return formats;
	}

	public void stopRepeatingRequests( )
			throws CameraAccessException
	{
		if ( cameraCaptureSession != null )
		{
			cameraCaptureSession.stopRepeating( );
			cameraCaptureSession.close( );
			cameraCaptureSession = null;
		}

		if ( cameraDevice != null )
		{
			cameraDevice.close( );
			cameraDevice = null;
		}

		stopCameraCaptureThread( );
	}

	public String[] getAvailableCameras( )
			throws CameraAccessException
	{
		return cameraManager.getCameraIdList( );
	}

	@Override
	public void close( )
	{
		try
		{
			stopRepeatingRequests( );
		}
		catch ( Exception e )
		{
			handleException( e );
//			e.printStackTrace( );
		}
	}


	/* PRIVATE METHODS */

	private void initializeCameraStateCallback( )
	{
		cameraStateCallback = new CameraDevice.StateCallback( )
		{
			@Override
			public void onOpened( @NonNull CameraDevice cameraDevice )
			{
				CameraDataAcquirer.this.cameraDevice = cameraDevice;

				if ( onCameraOpenedRunnable != null )
				{
					onCameraOpenedRunnable.run( );
				}
			}

			@Override
			public void onDisconnected( @NonNull CameraDevice cameraDevice )
			{
				CameraDataAcquirer.this.cameraDevice = null;

				Log.e( TAG, "CAMERA DISCONNECTED\nCAMERA DISCONNECTED\nCAMERA DISCONNECTED\n" );

				if ( onCameraDisconnectedRunnable != null )
				{
					onCameraDisconnectedRunnable.run( );
				}
			}

			@Override
			public void onError( @NonNull CameraDevice cameraDevice, int i )
			{
				if ( onCameraErrorHandler != null )
				{
					onCameraErrorHandler.onError( cameraDevice, i );
				}
				else
				{
					Log.e( TAG, "Camera error, device id: " + cameraDevice.getId( ) );
				}
			}
		};
	}

	private void initializeSessionStateCallback( )
	{
		sessionStateCallback = new CameraCaptureSession.StateCallback( )
		{
			@Override
			public void onConfigured( @NonNull CameraCaptureSession session )
			{
				cameraCaptureSession = session;

				if ( onCameraSessionConfiguredRunnable != null )
				{
					onCameraSessionConfiguredRunnable.run( );
				}
			}

			@Override
			public void onConfigureFailed( @NonNull CameraCaptureSession session )
			{
				if ( onCameraSessionConfigureFailed != null )
				{
					onCameraSessionConfigureFailed.accept( session );
				}
			}
		};
	}

	private void resetCameraSession( )
	{
		startCameraCaptureThread( );
	}

	private void resetSensorSettings( )
			throws CameraAccessException
	{
		sensorSensitivity = getAvailableSensorSensitivity( ).getUpper( );

		exposureTime = getAvailableExposureTime( ).getUpper( );
	}

	private void startCameraCaptureThread( )
	{
		stopCameraCaptureThread( );

		cameraCaptureThread = new HandlerThread( "cameraCaptureThread" );
		cameraCaptureThread.start( );
		cameraCaptureHandler = new Handler( cameraCaptureThread.getLooper( ) );
	}

	private void stopCameraCaptureThread( )
	{
		if ( cameraCaptureThread == null )
		{
			return;
		}

		cameraCaptureThread.quitSafely( );
		try
		{
			cameraCaptureThread.join( );
			cameraCaptureThread = null;
			cameraCaptureHandler = null;
		}
		catch ( InterruptedException e )
		{
			handleException( e );
//			e.printStackTrace( );
		}
	}

	private void handleException( Exception e )
	{
		e.printStackTrace( );
	}
}

