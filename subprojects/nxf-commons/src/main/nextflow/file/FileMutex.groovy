package nextflow.file

import java.nio.channels.FileLock

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import nextflow.util.Duration

/**
 * Implements a mutual exclusive file lock strategy to prevent
 * two or more JVMs/process to access the same file or resource
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@CompileStatic
class FileMutex {

    String waitMessage

    String errorMessage

    Duration timeout

    File target

    long delayMillis = 100

    private long timeoutMillis

    FileMutex() { }

    FileMutex(File target) {
        this.target = target
    }

    FileMutex setTimeout( value ) {
        if( value instanceof Duration )
            timeoutMillis = value.millis
        else if( value instanceof CharSequence )
            timeoutMillis = Duration.of(value.toString()).millis
        else if( value instanceof Number )
            timeoutMillis = value.longValue()
        else if( value != null )
            throw new IllegalArgumentException("Not a valid Duration value: $value [${value.class.name}]")

        return this
    }


    FileMutex setWaitMessage( String str ) {
        this.waitMessage = str
        return this
    }

    FileMutex setErrorMessage( String str ) {
        this.errorMessage = str
        return this
    }


    def lock(Closure closure) {
        assert target
        final file = new RandomAccessFile(target, "rw")
        try {
            /*
             * wait to acquire a lock
             */
            boolean showed=0
            long max = timeout ? timeout.millis : Long.MAX_VALUE
            long begin = System.currentTimeMillis()
            FileLock lock
            while( !(lock=file.getChannel().tryLock()) )  {
                if( waitMessage && !showed ) {
                    log.info waitMessage
                    showed = true
                }

                if( System.currentTimeMillis()-begin > max )
                    throw new InterruptedException("Cannot acquire FileLock on $target")

                sleep delayMillis
            }

            /*
             * now it can do the job
             */
            try {
                return closure.call()
            }
            finally {
                lock.close()
            }
        }
        finally {
            file.close()
        }

    }
}
