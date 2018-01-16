import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.time.LocalTime;
import java.util.Arrays;

public class Main
{

//    private static final int metadataOffsetByteCount = 1000;
    private static String targetDir = "../../raw_data/";
    private static int formatLineIndex = 3;
    private static int timeStampLineIndex = 4;
    private static int sensitivityLineIndex = 6;
    private static String fileExtension = ".rnd";
    private static int port = 52345;

    public static void main(String[] args)
    {
        if ( args.length != 2 )
        {
            System.out.println( "Fully functional usage: ... <jar-file> <source-dir> <port-num>" );
        }

        if ( args.length > 0 )
        {
            targetDir = args[ 0 ];
        }

        if ( args.length > 1 )
        {
            port = Integer.parseInt( args[ 1 ] );
        }

        ServerSocket serverSocket;

        try
        {
            serverSocket = new ServerSocket( );
            serverSocket.bind( new InetSocketAddress( port ) );

            System.err.println( "Socket bound to port " + serverSocket.getLocalPort( ) );
        }
        catch ( Exception e )
        {
            e.printStackTrace( );

            return;
        }

        byte[] data = new byte[ 50000000 ];
        byte[] processedData = new byte[ 50000000 ];
        int lastByteIndex = 0;
        long totalBytes = 0;
        LocalTime initialTime = LocalTime.now( );

        while ( !serverSocket.isClosed( ) )
        {
            try ( Socket socket = serverSocket.accept( );
                    InputStream inputStream = socket.getInputStream( ) )
            {
                lastByteIndex = 0;

                socket.setSoTimeout( 5000 );
                System.out.println( "Connection from: " + socket.getRemoteSocketAddress( ).toString( ) );
                LocalTime connectionTime = LocalTime.now( );
                try
                {
                    while ( socket.isBound( ) )
                    {
//                    System.out.println( "Socket is kept alive" );
                        data[ lastByteIndex ] = ( byte ) inputStream.read( );
                        lastByteIndex += 1;

                        int available = inputStream.available( );
                        if ( available == 0 )
                        {
                            break;
                        }

                        inputStream.read( data, lastByteIndex, available );
                        lastByteIndex += available;
                    }
                }
                catch ( Exception e )
                {
                    System.err.println( "Connection refused" );

                    continue;
                }
                LocalTime dataTranamissionEndTime = LocalTime.now( );

                System.err.println( "Data transmission rate: " +
                        ( float )lastByteIndex * 8.0f /
                                ( Duration.between( connectionTime, dataTranamissionEndTime ).toMillis( ) * 1000.0f ) +
                                    "Mbps" );

                System.out.println( "Time: " + Duration.between( initialTime, LocalTime.now( ) ).toString( ) +
                        "; overall speed speed: " + ( float )totalBytes * 0.008f /
                        ( float )Duration.between( initialTime, LocalTime.now( ) ).toMillis( ) + "Mbps");

                int endlCount = 0;
                int metaDataLength = 0;
                String format = "";
                String timeStamp = "";
                String sensitivityString = "";
                for ( metaDataLength = 0; endlCount < 16; metaDataLength += 1 )
                {
                    if ( ( char )data[ metaDataLength ] == '\n' )
                    {
                        endlCount += 1;
                    }
                    if ( endlCount == formatLineIndex && ( char )data[ metaDataLength ] != '\n' )
                    {
                        format += ( char )data[ metaDataLength ];
                    }
                    if ( endlCount == timeStampLineIndex && ( char )data[ metaDataLength ] != '\n' )
                    {
                        timeStamp += ( char )data[ metaDataLength ];
                    }
                    if ( endlCount == sensitivityLineIndex && ( char )data[ metaDataLength ] != '\n' )
                    {
                        sensitivityString += ( char )data[ metaDataLength ];
                    }
                }
                metaDataLength -= 1;

                int sensitivity = 0;
                try
                {
                    sensitivity = Integer.parseInt( sensitivityString );
                }
                catch ( Exception e )
                {
                    sensitivity = 0;
                }
                if ( sensitivity < 400 )
                {
                    System.err.println( "\n ---!!!---\n!!! Sensitivity is less than 400, skipping frame\n ---!!!---\n" );
                    continue;
                }

                String metaData = new String( Arrays.copyOfRange( data, 0, metaDataLength ) );
                System.err.println( "Meta data:\n" + metaData );

                totalBytes += lastByteIndex;

                int[] byteStats = new int[ 256 ];
                int[] bitStats = new int[ 2 ];

                int lastProcessedByteIndex = 0;
                for ( int i = 0; i < lastByteIndex; i += 1 )
                {
                    processedData[ lastProcessedByteIndex ] = data[ i ];
                    lastProcessedByteIndex += 1;

                    byteStats[ Byte.toUnsignedInt( data[ i ] ) ] += 1;
                    bitStats[ Byte.toUnsignedInt( data[ i ] ) % 2 ] += 1;
                }
/*
                for ( int i = 0; i < 256; i += 1 )
                {
                    System.out.print( String.format( "%06d", i ) + ":" + String.format( "%06d", byteStats[ i ] ) + " " );
                    if ( i % 16 == 15 )
                    {
                        System.out.println( );
                    }
                }*/

                System.out.println( "Even:" + String.format( "%08d", bitStats[ 0 ] ) );
                System.out.println( "Uneven:" + String.format( "%08d", bitStats[ 1 ] ) );

                System.out.println( "Total data: " + ( float )totalBytes / 1000000.0f + "MB" );

                System.out.println( "Data length: " + lastByteIndex );
                System.out.println( "Processed data length: " + lastProcessedByteIndex );

                /*
                for ( int i = 12000; i < 12000 + 100; i += 1 )
                {
                    System.out.print( Byte.toUnsignedInt( data[ i ] ) + " " );
                    if ( i % 20 == 19 )
                        System.out.println( );
                }*/

                String finalTimeStamp = timeStamp;
                String finalFormat = format;
                int finalLastByteIndex = lastByteIndex;
                int finalLastProcessedByteIndex = lastProcessedByteIndex;
                ( new Thread( ( ) -> {
                    if ( args.length <= 2 )
                    {
                        String fileName = finalTimeStamp + "_" + finalFormat + fileExtension;
                        File file = new File( targetDir + fileName );
                        try
                        {
                            if ( !file.createNewFile( ) )
                            {
                                System.err.println( "Cannot create new file: \'" + file.getAbsolutePath( ) + "\'" );

                                return;
                            }
                        }
                        catch ( Exception e )
                        {
                            e.printStackTrace( );
                        }

                        try ( FileOutputStream stream = new FileOutputStream( file ) )
                        {
    //                        stream.write( data, 0, finalLastByteIndex );
                                stream.write( processedData, 0, finalLastProcessedByteIndex );
                        }
                        catch ( Exception e )
                        {
                            e.printStackTrace( );
                        }
                    }
                } ) ).start( );
            }
            catch ( Exception e )
            {
                e.printStackTrace( );
            }
        }

//        System.out.println("Hello World!");
    }
}
