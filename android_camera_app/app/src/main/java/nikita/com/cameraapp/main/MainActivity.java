package nikita.com.cameraapp.main;

import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.camera2.*;
import android.media.Image;
import android.os.Build;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.Pair;
import android.util.Range;
import android.view.View;
import android.widget.*;
import nikita.com.cameraapp.R;
import nikita.com.cameraapp.dataacquirer.CameraDataAcquirer;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends AppCompatActivity
{
	private final boolean doUseRepeatingRequests = true;

	private final int maxDataQueueSize = 16;
	private final String TAG = "CAMERA_SENSOR_ACQUIRER";
	private final String DEFAULT_CAMERA_ID = "0";
	private final int metadataOffsetByteCount = 1000;
	private final String[] dataProcessingMethods = new String[]{ "NONE", "BYTE_XOR", "EACH_UNEVEN", "EACH_UNEVEN+BYTE_XOR",
			"EACH_UNEVEN+XOR(5*2&1)" };


	private CameraManager cameraManager;
	private CameraDataAcquirer dataAcquirer = null;
	private AtomicBoolean isCaptureOn = new AtomicBoolean( false );
	private AtomicBoolean isCaptureOnInitialization = new AtomicBoolean( false );
	private int dataProcessingMethodIndex = 0;

	ConcurrentLinkedQueue< Pair< byte[], String > > dataToBeTransferredQueue = new ConcurrentLinkedQueue<>( );
	AtomicInteger totalFramesSent = new AtomicInteger( 0 );
	AtomicInteger totalFramesDropped = new AtomicInteger( 0 );


	public MainActivity( )
	{
		super( );
	}

	@Override
	public void finish( )
	{
		dataAcquirer.close( );

		super.finish( );
	}

	@Override
	protected void onCreate( Bundle savedInstanceState )
	{
		super.onCreate( savedInstanceState );

		if ( dataAcquirer == null )
		{
			try
			{
				dataAcquirer = new CameraDataAcquirer( this );

				dataAcquirer.setOnCameraOpened( this::onCameraOpen );

				dataAcquirer.setOnCameraSessionConfigured( this::onCameraSessionConfigured );

				dataAcquirer.setOnCameraSessionConfigureFailed( this::onCameraSessionConfigureFailed );

				dataAcquirer.setCameraDataReader( ( byte[] data, CameraDataAcquirer.CaptureInfo captureInfo ) ->
						{
							/*
							if ( dataToBeTransferredQueue.size( ) < maxDataQueueSize )
							{
								Log.e( TAG, "New data added to queue, queue size = " + dataToBeTransferredQueue.size( ) );

								new Thread( ( ) ->
											readImage( data, captureInfo )
								).start( );

								totalFramesSent.incrementAndGet( );
							}
							else
							{
								Log.e( TAG, "Data DROPPED, queue size = " + dataToBeTransferredQueue.size( ) );

								totalFramesDropped.incrementAndGet( );
							}

							Log.e( TAG, "Frames sent: " + totalFramesSent.get( ) +
									"; frames dropped: " + totalFramesDropped.get( ) );

							runOnUiThread( ( ) ->
									{
										TextView text = findViewById( R.id.text );

										text.setText( "Frames sent: " + totalFramesSent.get( ) +
												"; frames dropped: " + totalFramesDropped.get( ) +
												"\nQueue size: " + dataToBeTransferredQueue.size( )
										);
									}
							);*/

							

							try
							{
							}
							catch ( Exception e )
							{
								e.printStackTrace( );
							}

							Log.e( TAG, "NEW FRAME" );

							if ( !doUseRepeatingRequests )
							{
								if ( isCaptureOn.get( ) )
								{
									try
									{
										dataAcquirer.requestCapture( );
									}
									catch ( Exception e )
									{
										Log.e( TAG, "REQUEST CAPTURE SUCKED\nREQUEST CAPTURE SUCKED\n" +
												"REQUEST CAPTURE SUCKED\nREQUEST CAPTURE SUCKED\n" +
												"REQUEST CAPTURE SUCKED\nREQUEST CAPTURE SUCKED\n" +
												"REQUEST CAPTURE SUCKED\nREQUEST CAPTURE SUCKED\n" );
										handleException( e );
									}
								}
							}
						}
				);

				dataAcquirer.setImageFormat( ImageFormat.RAW_SENSOR );

				dataAcquirer.setCameraId( DEFAULT_CAMERA_ID );
			}
			catch ( Exception e )
			{
				e.printStackTrace( );

				handleException( e );
//				Log.e( TAG, "Cannot instantiate data acquirer, quit" );

				finishAffinity( );
			}
		}

		initializeUI( );

		new Thread( ( ) ->
				{
					while ( true )
					{
						while ( dataToBeTransferredQueue.size( ) == 0 )
						{
							try
							{
								Thread.sleep( 20 );
							}
							catch ( Exception e )
							{
								// Ignoring Exception
							}
						}
						Pair < byte[], String > dataToBeTransferred = dataToBeTransferredQueue.poll( );

						String address = ( ( EditText ) findViewById( R.id.server_address_edit ) ).getText( )
								.toString( );
						String port = ( ( EditText ) findViewById( R.id.server_port_edit ) ).getText( ).toString( );
						Socket socket = new Socket( );

						try
						{
							socket.setSoTimeout( 1000 );
							Log.d( TAG, "Socket connecting..." );
							socket.connect( new InetSocketAddress( address, Integer.parseInt( port ) ), 1000 );
							Log.d( TAG, "Socket connected" );
						}
						catch ( Exception e )
						{
							Log.e( TAG, "Socket NOT connected" );
//							handleException( e );

							continue;
						}

						try ( OutputStream stream = socket.getOutputStream( ) )
						{
							stream.write( dataToBeTransferred.second.getBytes( ) );
							stream.write( dataToBeTransferred.first ); // , 0, 1000000 );
						}
						catch ( Exception e )
						{
							Log.e( TAG, "Streaming refused" );
//							handleException( e );
						}

//						totalFramesSent.incrementAndGet( );

//						Log.e( TAG, "Total frames sent: " + totalFramesSent.get( ) );
					}
				}
		).start( );

//		initCamera( );
	}

	private byte[] getProcessedCopyOf( byte[] data )
	{
		switch ( dataProcessingMethodIndex )
		{
			case 0:
				return data;
			case 1:
				return getByteXor( data );
			case 2:
				return getEachUneven( data );
			case 3:
				return getByteXor( getEachUneven( data ) );
			case 4:
				return getFiveTwoPlusOne( getEachUneven( data ) );
		}

		throw new AssertionError( "Unknown processing method" );
	}

	private void readImage( byte[] data, CameraDataAcquirer.CaptureInfo captureInfo )
	{
		byte[] processedData = getProcessedCopyOf( data );

		StringBuilder metadataBuilder = new StringBuilder( );

		metadataBuilder.append( Build.MANUFACTURER + " " + Build.BRAND + " " + Build.MODEL );
		metadataBuilder.append( "\n" );
		metadataBuilder.append( captureInfo.imageWidth );
		metadataBuilder.append( "\n" );
		metadataBuilder.append( captureInfo.imageHeight );
		metadataBuilder.append( "\n" );
		metadataBuilder.append( getImageFormatString( captureInfo.imageFormat ) );
		metadataBuilder.append( "\n" );
		metadataBuilder.append( DateTimeFormatter.ofPattern( "yyyy.MM.dd-HH.mm.ss.SSS" ).format(
				LocalDateTime.now( ) ) );
		metadataBuilder.append( "\n" );
		metadataBuilder.append( captureInfo.exposureTime.toString( ) );
		metadataBuilder.append( "\n" );
		metadataBuilder.append( captureInfo.sensitivity.toString( ) );
		metadataBuilder.append( "\n" );
		metadataBuilder.append( dataProcessingMethods[ dataProcessingMethodIndex ] );
		metadataBuilder.append( "\n" );
		metadataBuilder.append( "\n" );
		metadataBuilder.append( "\n" );
		metadataBuilder.append( "\n" );
		metadataBuilder.append( "\n" );
		metadataBuilder.append( "\n" );
		metadataBuilder.append( "\n" );
		metadataBuilder.append( "\n" );
		metadataBuilder.append( "\n" );

//		Log.d( TAG, "Data: " + ( processedData.length + metadataBuilder.length( ) ) + " bytes" );

		dataToBeTransferredQueue.add( new Pair<>( processedData, metadataBuilder.toString( ) ) );

		String s = "";
/*
		long[] stat = new long[ 256 ];
		long ones = 0;
		long zeros = 0;
		for ( int i = 0; i < processedData.length; i += 1 )
		{
			stat[ Byte.toUnsignedInt( processedData[ i ] ) ] += 1;
			for ( int j = 0; j < 8; j += 1 )
			{
				if ( ( Byte.toUnsignedInt( processedData[ i ] ) & ( 1 << j ) ) == 0 )
					zeros += 1;
				else
					ones += 1;
			}
		}
		for ( int i = 0; i < 256; i += 1 )
		{
			s += i + ":" + stat[ i ] + " ";
			if ( i % 8 == 7 )
			{
				s += "\n";
			}
		}
		s += "ones: " + ones + "\nzeros: " + zeros + "\n";
*/
/*
		for ( int i = 120000; i < 120000 + 30; i += 1 )
		{
			s += Integer.toHexString( Byte.toUnsignedInt( processedData[ i ] ) ) + " ";
			if ( i % 20 == 19 )
				s += "\n";
		}*/

//		Log.d( TAG, s );
		/////// TEST <<<<<<<<<<<<<<<<<<<<<<


	}

	private byte[] getByteXor( byte[] data )
	{
		byte[] newData = new byte[ data.length / 8 ];
		for ( int i = 0; i < data.length / 8; i += 1 )
		{
			byte newByte = 0;
			for ( int j = 0; j < 8; j += 1 )
			{
				byte dataByte = data[ i * 8 + j ];
				byte newBit = 0;
				for ( int k = 0; k < 8; k += 1 )
				{
					if ( ( dataByte & ( 1 << k ) ) != 0 )
					{
						newBit ^= 1;
					}
				}
				newByte += newBit << j;
			}
			newData[ i ] = newByte;
		}

		return newData;
	}

	private byte[] getEachUneven( byte[] data )
	{
		byte[] newData = new byte[ data.length / 2 ];
		for ( int i = 0; i < data.length / 2; i += 1 )
		{
			newData[ i ] = data[ 2 * i ];
		}

		return newData;
	}

	private byte[] getFiveTwoPlusOne( byte[] data )
	{
		byte[] newData = new byte[ data.length * 6 / 11 ];
		int i = 0;
		int j = 0;
		while ( i < data.length - 10 && j < newData.length - 5 )
		{
			newData[ j + 0 ] |= (byte)((((data[ i + 0  ] >> 7) & 0b1) ^ ((data[ i + 0  ] >> 6) & 0b1)) << 7);
			newData[ j + 0 ] |= (byte)((((data[ i + 0  ] >> 5) & 0b1) ^ ((data[ i + 0  ] >> 4) & 0b1)) << 6);
			newData[ j + 0 ] |= (byte)((((data[ i + 0  ] >> 3) & 0b1) ^ ((data[ i + 0  ] >> 2) & 0b1)) << 5);
			newData[ j + 0 ] |= (byte)((((data[ i + 0  ] >> 1) & 0b1) ^ ((data[ i + 0  ] >> 0) & 0b1)) << 4);
			newData[ j + 0 ] |= (byte)((((data[ i + 1  ] >> 7) & 0b1) ^ ((data[ i + 1  ] >> 6) & 0b1)) << 3);
			newData[ j + 0 ] |= (byte) (((data[ i + 1  ] >> 5) & 0b1)                                  << 2);
			newData[ j + 0 ] |= (byte)((((data[ i + 1  ] >> 4) & 0b1) ^ ((data[ i + 1  ] >> 3) & 0b1)) << 1);
			newData[ j + 0 ] |= (byte)((((data[ i + 1  ] >> 2) & 0b1) ^ ((data[ i + 1  ] >> 1) & 0b1)) << 0);
			newData[ j + 1 ] |= (byte)((((data[ i + 1  ] >> 0) & 0b1) ^ ((data[ i + 2  ] >> 7) & 0b1)) << 7);
			newData[ j + 1 ] |= (byte)((((data[ i + 2  ] >> 6) & 0b1) ^ ((data[ i + 2  ] >> 5) & 0b1)) << 6);
			newData[ j + 1 ] |= (byte)((((data[ i + 2  ] >> 4) & 0b1) ^ ((data[ i + 2  ] >> 3) & 0b1)) << 5);
			newData[ j + 1 ] |= (byte) (((data[ i + 2  ] >> 2) & 0b1)                                  << 4);
			newData[ j + 1 ] |= (byte)((((data[ i + 2  ] >> 1) & 0b1) ^ ((data[ i + 2  ] >> 0) & 0b1)) << 3);
			newData[ j + 1 ] |= (byte)((((data[ i + 3  ] >> 7) & 0b1) ^ ((data[ i + 3  ] >> 6) & 0b1)) << 2);
			newData[ j + 1 ] |= (byte)((((data[ i + 3  ] >> 5) & 0b1) ^ ((data[ i + 3  ] >> 4) & 0b1)) << 1);
			newData[ j + 1 ] |= (byte)((((data[ i + 3  ] >> 3) & 0b1) ^ ((data[ i + 3  ] >> 2) & 0b1)) << 0);
			newData[ j + 2 ] |= (byte)((((data[ i + 3  ] >> 1) & 0b1) ^ ((data[ i + 3  ] >> 0) & 0b1)) << 7);
			newData[ j + 2 ] |= (byte) (((data[ i + 4  ] >> 7) & 0b1)                                  << 6);
			newData[ j + 2 ] |= (byte)((((data[ i + 4  ] >> 6) & 0b1) ^ ((data[ i + 4  ] >> 5) & 0b1)) << 5);
			newData[ j + 2 ] |= (byte)((((data[ i + 4  ] >> 4) & 0b1) ^ ((data[ i + 4  ] >> 3) & 0b1)) << 4);
			newData[ j + 2 ] |= (byte)((((data[ i + 4  ] >> 2) & 0b1) ^ ((data[ i + 4  ] >> 1) & 0b1)) << 3);
			newData[ j + 2 ] |= (byte)((((data[ i + 4  ] >> 0) & 0b1) ^ ((data[ i + 5  ] >> 7) & 0b1)) << 2);
			newData[ j + 2 ] |= (byte)((((data[ i + 5  ] >> 6) & 0b1) ^ ((data[ i + 5  ] >> 5) & 0b1)) << 1);
			newData[ j + 2 ] |= (byte) (((data[ i + 5  ] >> 4) & 0b1)                                  << 0);

			newData[ j + 3 ] |= (byte)((((data[ i + 5  ] >> 3) & 0b1) ^ ((data[ i + 5  ] >> 2) & 0b1)) << 7);
			newData[ j + 3 ] |= (byte)((((data[ i + 5  ] >> 1) & 0b1) ^ ((data[ i + 5  ] >> 0) & 0b1)) << 6);
			newData[ j + 3 ] |= (byte)((((data[ i + 6  ] >> 7) & 0b1) ^ ((data[ i + 6  ] >> 6) & 0b1)) << 5);
			newData[ j + 3 ] |= (byte)((((data[ i + 6  ] >> 5) & 0b1) ^ ((data[ i + 6  ] >> 4) & 0b1)) << 4);
			newData[ j + 3 ] |= (byte)((((data[ i + 6  ] >> 3) & 0b1) ^ ((data[ i + 6  ] >> 2) & 0b1)) << 3);
			newData[ j + 3 ] |= (byte) (((data[ i + 6  ] >> 1) & 0b1)                                  << 2);
			newData[ j + 3 ] |= (byte)((((data[ i + 6  ] >> 0) & 0b1) ^ ((data[ i + 7  ] >> 7) & 0b1)) << 1);
			newData[ j + 3 ] |= (byte)((((data[ i + 7  ] >> 6) & 0b1) ^ ((data[ i + 7  ] >> 5) & 0b1)) << 0);
			newData[ j + 4 ] |= (byte)((((data[ i + 7  ] >> 4) & 0b1) ^ ((data[ i + 7  ] >> 3) & 0b1)) << 7);
			newData[ j + 4 ] |= (byte)((((data[ i + 7  ] >> 2) & 0b1) ^ ((data[ i + 7  ] >> 1) & 0b1)) << 6);
			newData[ j + 4 ] |= (byte)((((data[ i + 7  ] >> 0) & 0b1) ^ ((data[ i + 8  ] >> 7) & 0b1)) << 5);
			newData[ j + 4 ] |= (byte) (((data[ i + 8  ] >> 6) & 0b1)                                  << 4);
			newData[ j + 4 ] |= (byte)((((data[ i + 8  ] >> 5) & 0b1) ^ ((data[ i + 8  ] >> 4) & 0b1)) << 3);
			newData[ j + 4 ] |= (byte)((((data[ i + 8  ] >> 3) & 0b1) ^ ((data[ i + 8  ] >> 2) & 0b1)) << 2);
			newData[ j + 4 ] |= (byte)((((data[ i + 8  ] >> 1) & 0b1) ^ ((data[ i + 8  ] >> 0) & 0b1)) << 1);
			newData[ j + 4 ] |= (byte)((((data[ i + 9  ] >> 7) & 0b1) ^ ((data[ i + 9  ] >> 6) & 0b1)) << 0);
			newData[ j + 5 ] |= (byte)((((data[ i + 9  ] >> 5) & 0b1) ^ ((data[ i + 9  ] >> 4) & 0b1)) << 7);
			newData[ j + 5 ] |= (byte) (((data[ i + 9  ] >> 3) & 0b1)                                  << 6);
			newData[ j + 5 ] |= (byte)((((data[ i + 9  ] >> 2) & 0b1) ^ ((data[ i + 9  ] >> 1) & 0b1)) << 5);
			newData[ j + 5 ] |= (byte)((((data[ i + 9  ] >> 0) & 0b1) ^ ((data[ i + 10 ] >> 7) & 0b1)) << 4);
			newData[ j + 5 ] |= (byte)((((data[ i + 10 ] >> 6) & 0b1) ^ ((data[ i + 10 ] >> 5) & 0b1)) << 3);
			newData[ j + 5 ] |= (byte)((((data[ i + 10 ] >> 4) & 0b1) ^ ((data[ i + 10 ] >> 3) & 0b1)) << 2);
			newData[ j + 5 ] |= (byte)((((data[ i + 10 ] >> 2) & 0b1) ^ ((data[ i + 10 ] >> 1) & 0b1)) << 1);
			newData[ j + 5 ] |= (byte) (((data[ i + 10 ] >> 0) & 0b1)                                  << 0);

			i += 11;
			j += 6;
		}

		return newData;
	}

	private String getImageFormatString( int imageFormat )
	{
		switch ( imageFormat )
		{
			case ImageFormat.RAW_SENSOR:
				return "RAW";
			case ImageFormat.YUV_420_888:
				return "YUV_420_888";
			case ImageFormat.JPEG:
				return "JPEG";
			case ImageFormat.RAW10:
				return "RAW10";
			case ImageFormat.RAW12:
				return "RAW12";
		}

		return "UNKNOWN";
	}

	private void handleException( Exception e )
	{
		e.printStackTrace( );

		Toast toast = Toast.makeText( this,
				"Error: " + e.getMessage( ),
				Toast.LENGTH_SHORT );
		toast.show( );
	}

	private void onCameraOpen( )
	{
		try
		{
			dataAcquirer.initializeCaptureSession( );
		}
		catch ( Exception e )
		{
//			dataAcquirer.close( );

			handleException( e );
/*			e.printStackTrace( );

			Toast toast = Toast.makeText( this,
					"Error: " + e.getMessage( ),
					Toast.LENGTH_LONG );
			toast.show( );*/
		}
	}

	private void onCameraSessionConfigured( )
	{
		try
		{
//			dataAcquirer.startRepeatingRequests( );
		}
		catch ( Exception e )
		{
			handleException( e );
//			e.printStackTrace( );
		}

		findViewById( R.id.start_button ).setEnabled( true );

		isCaptureOnInitialization.set( false );
	}

	private void onCameraSessionConfigureFailed( CameraCaptureSession session )
	{
//		dataAcquirer.close( );

		Log.e( TAG, "Failed to configure session" );

		Toast toast = Toast.makeText( this,
				"Failed to configure camera capture session",
				Toast.LENGTH_SHORT );
		toast.show( );
	}

	private void initializeUI( )
	{
		setContentView( R.layout.activity_main );

		Button startButton = findViewById( R.id.start_button );
		startButton.setOnClickListener( ( e ) ->
				{
					try
					{
						if ( !isCaptureOn.get( ) )
						{
							isCaptureOn.set( true );
							Thread.sleep( 100 );
							startButton.setText( R.string.start_button_text_stop );

							int attemptCount = 10;
							int i;
							for ( i = 0; i < attemptCount; i += 1 )
							{
								try
								{


									if ( doUseRepeatingRequests )
									{
										dataAcquirer.startRepeatingRequests( );
									}
									else
									{
										dataAcquirer.requestCapture( );
									}

									Log.d( TAG, "Capture requested on attempt #" + i );
									break;
								}
								catch ( CameraAccessException cameraException )
								{

								}
							}
							if ( i == 10 )
							{
								dataAcquirer.close( );

								dataAcquirer.openCamera( );

								Log.e( TAG, "Capture error!" );
							}
						}
						else
						{
							isCaptureOn.set( false );

							startButton.setText( R.string.start_button_text_start );


							if ( doUseRepeatingRequests )
							{
								dataAcquirer.stopRepeatingRequests( );
							}
						}
					}
					catch ( Exception ex )
					{
						handleException( ex );
					}
				}
		);

		cameraManager = ( CameraManager )getSystemService( Context.CAMERA_SERVICE );
		String[] cameraList = new String[ 0 ];
		try
		{
			cameraList = cameraManager.getCameraIdList( );
		}
		catch ( Exception e )
		{
			handleException( e );
/*
			e.printStackTrace( );

			Toast toast = Toast.makeText( this,
					"Error: " + e.getMessage( ),
					Toast.LENGTH_LONG );
			toast.show( );*/
		}

		Spinner cameraSpinner = findViewById( R.id.camera_spinner );
		ArrayAdapter<String> cameraSpinnerAdapter = new ArrayAdapter<>( this, android.R.layout.simple_spinner_item, cameraList );
		cameraSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		String[] finalCameraList = cameraList;
		cameraSpinner.setAdapter( cameraSpinnerAdapter );

		Spinner formatSpinner = findViewById( R.id.format_spinner );
		List< Pair< Integer, String > > formats = dataAcquirer.getAvailableFormats( );
		String[] formatNames = new String[ formats.size( ) ];
		int[] formatValues = new int[ formats.size( ) ];
		for ( int i = 0; i < formats.size( ); i += 1 )
		{
			formatNames[ i ] = formats.get( i ).second;
			formatValues[ i ] = formats.get( i ).first;
		}
		ArrayAdapter<String> imageFormatSpinnerAdapter = new ArrayAdapter<>( this, android.R.layout.simple_spinner_item, formatNames );
		imageFormatSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		formatSpinner.setAdapter( imageFormatSpinnerAdapter );

		Spinner processMethodSpinner = findViewById( R.id.process_method_spinner );
		ArrayAdapter<String> processMethodSpinnerAdapter = new ArrayAdapter<>( this, android.R.layout.simple_spinner_item, dataProcessingMethods );
		processMethodSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		processMethodSpinner.setAdapter( processMethodSpinnerAdapter );

		SeekBar sensitivitySeekBar = findViewById( R.id.sensitivity_bar );
		SeekBar exposureTimeSeekBar = findViewById( R.id.exposure_time_bar );

		sensitivitySeekBar.setOnSeekBarChangeListener( new SeekBar.OnSeekBarChangeListener( )
		{
			@Override
			public void onProgressChanged( SeekBar seekBar, int i, boolean b )
			{
//				initCamera( );
				setCaptureSettings( );
			}

			@Override
			public void onStartTrackingTouch( SeekBar seekBar )
			{}

			@Override
			public void onStopTrackingTouch( SeekBar seekBar )
			{}
		} );

		exposureTimeSeekBar.setOnSeekBarChangeListener( new SeekBar.OnSeekBarChangeListener( )
		{
			@Override
			public void onProgressChanged( SeekBar seekBar, int i, boolean b )
			{
//				initCamera( );
				setCaptureSettings( );
			}

			@Override
			public void onStartTrackingTouch( SeekBar seekBar )
			{}

			@Override
			public void onStopTrackingTouch( SeekBar seekBar )
			{}
		} );

		cameraSpinner.setOnItemSelectedListener( new AdapterView.OnItemSelectedListener( )
		{
			@Override
			public void onItemSelected( AdapterView < ? > adapterView, View view, int i, long l )
			{
				try
				{
					//					dataAcquirer.close( );
					initCamera( );
				}
				catch ( Exception e )
				{
					handleException( e );
				}
			}

			@Override
			public void onNothingSelected( AdapterView < ? > adapterView )
			{
				try
				{
					//					dataAcquirer.close( );
					//					initCamera( );
				}
				catch ( Exception e )
				{
					handleException( e );
				}
			}
		} );

		formatSpinner.setOnItemSelectedListener( new AdapterView.OnItemSelectedListener( )
		{
			@Override
			public void onItemSelected( AdapterView < ? > adapterView, View view, int i, long l )
			{
				try
				{
					//					dataAcquirer.close( );
					initCamera( );
				}
				catch ( Exception e )
				{
					handleException( e );
				}
			}

			@Override
			public void onNothingSelected( AdapterView < ? > adapterView )
			{
				try
				{
					//					dataAcquirer.close( );
					//					initCamera( );
				}
				catch ( Exception e )
				{
					handleException( e );
				}
			}
		} );

		processMethodSpinner.setOnItemSelectedListener( new AdapterView.OnItemSelectedListener( )
		{
			@Override
			public void onItemSelected( AdapterView < ? > adapterView, View view, int i, long l )
			{
				try
				{
					//					dataAcquirer.close( );
					setCaptureSettings( );
				}
				catch ( Exception e )
				{
					handleException( e );
				}
			}

			@Override
			public void onNothingSelected( AdapterView < ? > adapterView )
			{
				try
				{
					//					dataAcquirer.close( );
					//					initCamera( );
				}
				catch ( Exception e )
				{
					handleException( e );
				}
			}
		} );

		setCaptureSettings( );
	}

	private void initCamera( )
	{
		if ( isCaptureOnInitialization.get( ) )
		{
			return;
		}

		isCaptureOnInitialization.set( true );
		isCaptureOn.set( false );

		Spinner cameraSpinner = findViewById( R.id.camera_spinner );
		Spinner formatSpinner = findViewById( R.id.format_spinner );

		try
		{
			dataAcquirer.setCameraId( cameraManager.getCameraIdList( )[ cameraSpinner.getSelectedItemPosition( ) ] );
			dataAcquirer.setImageFormat( dataAcquirer.getAvailableFormats( ).get( formatSpinner.getSelectedItemPosition( ) ).first );
			dataAcquirer.openCamera( );
		}
		catch ( Exception e )
		{
			handleException( e );
		}

		setCaptureSettings( );
	}

	private void setCaptureSettings( )
	{
		SeekBar sensitivitySeekBar = findViewById( R.id.sensitivity_bar );
		SeekBar exposureTimeSeekBar = findViewById( R.id.exposure_time_bar );

		TextView sensitivityValueView = findViewById( R.id.sensitivity_value );
		TextView sensitivityRangeView = findViewById( R.id.sensitivity_range );
		TextView exposureTimeValueView = findViewById( R.id.exposure_time_value );
		TextView exposureTimeRangeView = findViewById( R.id.exposure_time_range );

		Integer sensitivity = sensitivitySeekBar.getProgress( );
		Long exposureTime = ( long )exposureTimeSeekBar.getProgress( );

		sensitivityValueView.setText( sensitivity.toString( ) );
		exposureTimeValueView.setText( exposureTime.toString( ) );

		dataProcessingMethodIndex = ( ( Spinner )findViewById( R.id.process_method_spinner ) ).getSelectedItemPosition( );

		try
		{
			sensitivityRangeView.setText( "[" + dataAcquirer.getAvailableSensorSensitivity( ).getLower( ) +
					";" + dataAcquirer.getAvailableSensorSensitivity( ).getUpper( ) + "]" );
			exposureTimeRangeView.setText( "[" + dataAcquirer.getAvailableExposureTime( ).getLower( ) +
					";" + dataAcquirer.getAvailableExposureTime( ).getUpper( ) + "]" );

			sensitivitySeekBar.setMin( dataAcquirer.getAvailableSensorSensitivity( ).getLower( ) );
			sensitivitySeekBar.setMax( dataAcquirer.getAvailableSensorSensitivity( ).getUpper( ) );

			exposureTimeSeekBar.setMin( dataAcquirer.getAvailableExposureTime( ).getLower( ).intValue( ) );
			exposureTimeSeekBar.setMax( dataAcquirer.getAvailableExposureTime( ).getUpper( ).intValue( ) );
		}
		catch ( Exception e )
		{
			handleException( e );
		}
/*
		sensitivitySeekBar.setProgress( 6400 );
		exposureTimeSeekBar.setProgress( 35000000 );
*/
		dataAcquirer.setExposureTime( exposureTime );
		dataAcquirer.setSensorSensitivity( sensitivity );
	}
}
