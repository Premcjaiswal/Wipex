package com.zerowipe.nativelayer;

/**
 * Raw {@code STORAGE_BUS_TYPE} values (from {@code winioctl.h}) that this
 * project's domain {@code BusType} enum distinguishes. Public - unlike the
 * IOCTL plumbing in this package, the device layer needs these to map
 * {@link AdapterDescriptor#busTypeRaw()} and {@link DeviceDescriptor#busTypeRaw()}
 * without duplicating the magic numbers.
 *
 * <p>The full {@code STORAGE_BUS_TYPE} enum has ~20 members (Scsi, Atapi,
 * Ata, 1394, Ssa, Fibre, iScsi, Sd, Mmc, Virtual, FileBackedVirtual,
 * Spaces, SCM, Ufs, ...); only the ones {@code BusType} distinguishes are
 * listed here; anything else maps to {@code BusType.UNKNOWN}.
 */
public final class StorageBusTypeValues {

    private StorageBusTypeValues() {
    }

    public static final int UNKNOWN = 0x00;
    public static final int USB = 0x07;
    public static final int RAID = 0x08;
    public static final int SAS = 0x0A;
    public static final int SATA = 0x0B;
    public static final int NVME = 0x11;
}
