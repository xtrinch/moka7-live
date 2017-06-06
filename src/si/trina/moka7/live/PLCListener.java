package si.trina.moka7.live;

public interface PLCListener {			
	abstract public void PLCBitChanged(int address, int pos, boolean val, String plcName);
}
