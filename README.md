[![Maven Central](https://maven-badges.herokuapp.com/maven-central/si.trina/moka7-live/badge.svg)](https://maven-badges.herokuapp.com/maven-central/si.trina/moka7-live)


# moka7-live

This is a library built around [moka7](http://snap7.sourceforge.net/moka7.html) created by Dave Nardella. Moka7 is is the Java port of Snap7 Client. It’s a pure Java implementation of the S7Protocol used to communicate with S7 PLCs.



## Installation

Package can be installed via maven by adding the following to your pom.xml:

    <dependency>
        <groupId>si.trina</groupId>
        <artifactId>moka7-live</artifactId>
        <version>0.0.9</version>
    </dependency>
    
## How to use

**1. Create classes that implement interface PLCListener**

**2. Create PLC class instances for every PLC you wish to receive bit changes / read integers, bits from**

``` 
import si.trina.moka7.live.PLC;

/*
    args: 
        ** name of PLC
        ** IP of PLC
        ** byte array with length of db PLC->PC
        ** byte array with length of PC->PLC
        ** db (DataBase) number PLC->PC
        ** db (DataBase) number PC->PLC
        ** array of addresses of booleans to listen to changes to
*/
PLC plc1 = new PLC("Test PLC1","10.10.21.10",new byte[32],new byte[36],112,114,new double[]{0.1,0.2});
PLC plc2 = new PLC("Test PLC2", "10.10.22.10",new byte[18],new byte[22],45,44,new double[]{0.1,0.2,0.3}); 
```

**3. Add classes that implement interface PLCListener to PLC's `ArrayList<PLCListener> listener` array**

```
PLCListenerImplementation myListener = new PLCListenerImplementation();
plc1.listeners.add(myListener);
plc2.listeners.add(myListener);
```

**4. Start a thread for each PLC instance**

```
Thread t1 = new Thread(plc1).start();
Thread t2 = new Thread(plc2).start();
```

**5. Receive bit changes from bits at addresses from last argument of PLC constructor**

```
import si.trina.moka7.live.PLCListener;

public class PLCListenerImplementation implements PLCListener {
    @Override
    public void PLCBitChanged(int address, int pos, boolean val, String plcName) {
        switch (address) {
        case 0:
            switch (pos) {
            case 1:
                System.out.println("Bit at address 0.1 of PLC " + plcName + " changed to: " + val);
            }
        }
    }
}
```

**6. Write shorts/integers to DB**

```
/*
    args: 
        ** database to write to: from plc = true, from pc = false
        ** address to write to
        ** short/integer to write to db
*/
plc1.putInt(false, 12, (short)3);
plc1.putDInt(false, 12, 3);
```

**7. Read shorts/integers from DB**
```
try {
    short aShort = plc1.getInt(true, 8); // 2 bytes
    int anInteger = plc1.getDInt(true, 8); // 4 bytes
} catch (Exception e) { 
    e.printStackTrace(); 
}
```

## Optional

**1. Check communication status**

Sets bit at address 0.0 in both DB's as the 'live bit', meaning it toggles it every 250ms and expects PLC to toggle it back. Throws exception if it doesn't.

```
    plc1.liveBitEnabled = true;
```
