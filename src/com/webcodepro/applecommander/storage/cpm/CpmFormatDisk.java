/*
 * AppleCommander - An Apple ][ image utility.
 * Copyright (C) 2003 by Robert Greene
 * robgreene at users.sourceforge.net
 *
 * This program is free software; you can redistribute it and/or modify it 
 * under the terms of the GNU General Public License as published by the 
 * Free Software Foundation; either version 2 of the License, or (at your 
 * option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but 
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY 
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License 
 * for more details.
 *
 * You should have received a copy of the GNU General Public License along 
 * with this program; if not, write to the Free Software Foundation, Inc., 
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */
package com.webcodepro.applecommander.storage.cpm;

import com.webcodepro.applecommander.storage.DiskFullException;
import com.webcodepro.applecommander.storage.FileEntry;
import com.webcodepro.applecommander.storage.FormattedDisk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

/**
 * Manages a disk that is in the Apple CP/M format.
 * <p>
 * @author Rob Greene
 */
public class CpmFormatDisk extends FormattedDisk {
	/**
	 * The size of the CP/M sector.  Assumed to be 128.
	 */
	public static final int CPM_SECTORSIZE = 128;
	/**
	 * The size of a CP/M block.  Assumed to be 1K.
	 */
	public static final int CPM_BLOCKSIZE = 1024;
	/**
	 * The number of CP/M sectors per CP/M block.
	 */
	public static final int CPM_SECTORS_PER_CPM_BLOCK = 
		CPM_BLOCKSIZE / CPM_SECTORSIZE;
	/**
	 * The number of CP/M blocks per physical track.
	 */
	public static final int CPM_BLOCKS_PER_TRACK = 4;
	/**
	 * The number of physical sectors per CP/M block.
	 */
	public static final int PHYSICAL_SECTORS_PER_BLOCK = 4;
	/**
	 * The track number which CP/M block #0 resides at.
	 * (The other tracks are boot-related and not available.)
	 */
	public static final int PHYSICAL_BLOCK_TRACK_START = 3;
	/**
	 * The sector skew of the CP/M disk image.
	 */
	public static final int[] sectorSkew = {
						 0x0, 0x6, 0xc, 0x3, 0x9, 0xf, 0xe, 0x5, 
						 0xb, 0x2, 0x8, 0x7, 0xd, 0x4, 0xa, 0x1 };

	/**
	 * Manage CP/M disk usage.
	 */
	public class CpmDiskUsage implements DiskUsage {
		int block = -1;
		boolean[] usage = null;
		public CpmDiskUsage(boolean[] usage) {
			this.usage = usage;
		}
		public boolean hasNext() {
			return block < usage.length-1;
		}
		public void next() {
			block++;
		}
		public boolean isFree() {
			return !isUsed();
		}
		public boolean isUsed() {
			return usage[block];
		}
	}

	
	/**
	 * Create a CP/M formatted disk.
	 */
	public CpmFormatDisk(String filename, byte[] diskImage) {
		super(filename, diskImage);
	}

	/**
	 * There apparantly is no corresponding CP/M disk name.
	 * @see com.webcodepro.applecommander.storage.FormattedDisk#getDiskName()
	 */
	public String getDiskName() {
		return "CP/M Volume";
	}

	/**
	 * Identify the operating system format of this disk.
	 * @see com.webcodepro.applecommander.storage.FormattedDisk#getFormat()
	 */
	public String getFormat() {
		return "CP/M";
	}

	/**
	 * Return the amount of free space in bytes.
	 * @see com.webcodepro.applecommander.storage.FormattedDisk#getFreeSpace()
	 */
	public int getFreeSpace() {
		return getPhysicalSize() - getUsedSpace();
	}

	/**
	 * Return the amount of used space in bytes.
	 * @see com.webcodepro.applecommander.storage.FormattedDisk#getUsedSpace()
	 */
	public int getUsedSpace() {
		int blocksUsed = getBlocksUsed();
		return blocksUsed * CPM_BLOCKSIZE +
			PHYSICAL_BLOCK_TRACK_START * CPM_BLOCKS_PER_TRACK * CPM_BLOCKSIZE;
	}
	
	/**
	 * Compute the number of CP/M blocks that are currently used.
	 */
	public int getBlocksUsed() {
		List files = getFiles();
		int blocksUsed = 0;
		for (int i=0; i<files.size(); i++) {
			CpmFileEntry fileEntry = (CpmFileEntry) files.get(i);
			blocksUsed = fileEntry.getBlocksUsed();
		}
		return blocksUsed;
	}

	/**
	 * Get suggested dimensions for display of bitmap.
	 * Typically, this will be only used for 5.25" floppies.
	 * This can return null if there is no suggestion.
	 * @see com.webcodepro.applecommander.storage.FormattedDisk#getBitmapDimensions()
	 */
	public int[] getBitmapDimensions() {
		return null;
	}

	/**
	 * Get the length of the bitmap.
	 * This is hard-coded to 140.
	 * @see com.webcodepro.applecommander.storage.FormattedDisk#getBitmapLength()
	 */
	public int getBitmapLength() {
		return getPhysicalSize() / CPM_BLOCKSIZE;
	}

	/**
	 * Get the disk usage iterator.
	 * @see com.webcodepro.applecommander.storage.FormattedDisk#getDiskUsage()
	 */
	public DiskUsage getDiskUsage() {
		boolean[] usage = new boolean[getBitmapLength()];
		// fill in reserved space at beginning of disk (including 2 directory blocks)
		int dataBlockStart = PHYSICAL_BLOCK_TRACK_START * CPM_BLOCKS_PER_TRACK;
		for (int i=0; i<dataBlockStart+2; i++) {
			usage[i] = true;
		}
		// fill in space used by files
		List files = getFiles();
		for (int i=0; i<files.size(); i++) {
			CpmFileEntry fileEntry = (CpmFileEntry) files.get(i);
			int[] allocation = fileEntry.getAllocations();
			for (int a=0; a<allocation.length; a++) {
				int block = dataBlockStart + allocation[a];
				usage[block] = true;
			}
		}
		return new CpmDiskUsage(usage);
	}

	/**
	 * Get the labels to use in the bitmap.
	 * @see com.webcodepro.applecommander.storage.FormattedDisk#getBitmapLabels()
	 */
	public String[] getBitmapLabels() {
		return new String[] { "CP/M 1K BLOCK" };
	}

	/**
	 * Indicates if this disk format supports "deleted" files.
	 * @see com.webcodepro.applecommander.storage.FormattedDisk#supportsDeletedFiles()
	 */
	public boolean supportsDeletedFiles() {
		return true;
	}

	/**
	 * Indicates if this disk image can read data from a file.
	 * @see com.webcodepro.applecommander.storage.FormattedDisk#canReadFileData()
	 */
	public boolean canReadFileData() {
		return true;
	}

	/**
	 * Indicates if this disk image can write data to a file.
	 * @see com.webcodepro.applecommander.storage.FormattedDisk#canWriteFileData()
	 */
	public boolean canWriteFileData() {
		return false;
	}

	/**
	 * Identify if this disk format is capable of having directories.
	 * @see com.webcodepro.applecommander.storage.FormattedDisk#canHaveDirectories()
	 */
	public boolean canHaveDirectories() {
		return false;
	}

	/**
	 * Indicates if this disk image can delete a file.
	 * @see com.webcodepro.applecommander.storage.FormattedDisk#canDeleteFile()
	 */
	public boolean canDeleteFile() {
		return true;
	}

	/**
	 * Get the data associated with the specified FileEntry.
	 * This is just the raw data.  Use the FileEntry itself to read
	 * data appropriately!
	 * @see com.webcodepro.applecommander.storage.FormattedDisk#getFileData(com.webcodepro.applecommander.storage.FileEntry)
	 */
	public byte[] getFileData(FileEntry fileEntry) {
		CpmFileEntry cpmEntry = (CpmFileEntry) fileEntry;
		int[] allocations = cpmEntry.getAllocations();
		byte[] data = new byte[allocations.length * CPM_BLOCKSIZE];
		for (int i=0; i<allocations.length; i++) {
			int blockNumber = allocations[i];
			if (blockNumber > 0) {
				byte[] block = readCpmBlock(blockNumber);
				System.arraycopy(block, 0, 
					data, i * CPM_BLOCKSIZE, CPM_BLOCKSIZE);
			}
		}
		return data;
	}

	/**
	 * Format the disk.  Simply wipes the disk to all 0xE5 - this seems to
	 * be the standard (or a requirement). 
	 * <p>
	 * Note: Assumes that this is a 140K CP/M disk of 35 tracks and
	 * 16 physical sectors.
	 * @see com.webcodepro.applecommander.storage.FormattedDisk#format()
	 */
	public void format() {
		byte[] sectorData = new byte[SECTOR_SIZE];
		for (int i=0; i<SECTOR_SIZE; i++) {
			sectorData[i] = (byte) 0xe5;
		}
		for (int track=0; track<35; track++) {
			for (int sector=0; sector<16; sector++) {
				writeSector(track, sector, sectorData);
			}
		}
	}

	/**
	 * Returns the logical disk number.  This can be used to identify
	 * between disks when a format supports multiple logical volumes.
	 * If a value of 0 is returned, there is not multiple logical
	 * volumes to distinguish.
	 * @see com.webcodepro.applecommander.storage.FormattedDisk#getLogicalDiskNumber()
	 */
	public int getLogicalDiskNumber() {
		return 0;
	}

	/**
	 * Returns a valid filename for the given filename.  The assumed rules
	 * are that the name must be 8 characters long (padded on the end with
	 * spaces) and alphanumeric, starting with a character.
	 * @see com.webcodepro.applecommander.storage.FormattedDisk#getSuggestedFilename(java.lang.String)
	 */
	public String getSuggestedFilename(String filename) {
		StringTokenizer tokenizer = new StringTokenizer(filename, ".");
		filename = tokenizer.nextToken();	// grab just the first part of the name..
		StringBuffer newName = new StringBuffer();
		if (!Character.isLetter(filename.charAt(0))) {
			newName.append('A');
		}
		int i=0;
		while (newName.length() < 8 && i<filename.length()) {
			char ch = filename.charAt(i);
			if (Character.isLetterOrDigit(ch) || ch == '.') {
				newName.append(ch);
			}
			i++;
		}
		while (newName.length() < 8) newName.append(' ');
		return newName.toString().toUpperCase().trim();
	}

	/**
	 * Returns a valid filetype for the given filename.  Rules are very
	 * similar to the filename, but trim to 3 characters.
	 * @see com.webcodepro.applecommander.storage.FormattedDisk#getSuggestedFiletype(java.lang.String)
	 */
	public String getSuggestedFiletype(String filetype) {
		StringTokenizer tokenizer = new StringTokenizer(filetype, ".");
		tokenizer.nextToken();
		filetype = "";
		while (tokenizer.hasMoreTokens()) {
			filetype = tokenizer.nextToken();	// grab just the last part of the name...
		}
		StringBuffer newType = new StringBuffer();
		if (filetype.length() > 0 && !Character.isLetter(filetype.charAt(0))) {
			newType.append('A');
		}
		int i=0;
		while (newType.length() < 3 && i<filetype.length()) {
			char ch = filetype.charAt(i);
			if (Character.isLetterOrDigit(ch) || ch == '.') {
				newType.append(ch);
			}
			i++;
		}
		while (newType.length() < 3) newType.append(' ');
		return newType.toString().toUpperCase().trim();
	}

	/**
	 * Returns a list of possible file types.  Since the filetype is
	 * specific to each operating system, a simple String is used.
	 * @see com.webcodepro.applecommander.storage.FormattedDisk#getFiletypes()
	 */
	public String[] getFiletypes() {
		return null;	// there are no standard filetypes...
	}

	/**
	 * Indicates if this filetype requires an address component.
	 * @see com.webcodepro.applecommander.storage.FormattedDisk#needsAddress(java.lang.String)
	 */
	public boolean needsAddress(String filetype) {
		return false;
	}

	/**
	 * Answer with a list of file entries.
	 * @see com.webcodepro.applecommander.storage.DirectoryEntry#getFiles()
	 */
	public List getFiles() {
		List files = new ArrayList();
		Map index = new HashMap();
		for (int i=0; i<64; i++) {
			int offset = i*CpmFileEntry.ENTRY_LENGTH;
			CpmFileEntry fileEntry = new CpmFileEntry(this, offset);
			if (!fileEntry.isEmpty()) {
				// Files are unique by name, type, and user number.
				String key = fileEntry.getFilename().trim() + "." 
					+ fileEntry.getFiletype().trim() + ":"
					+ fileEntry.getUserNumber(0);
				if (index.containsKey(key)) {
					fileEntry = (CpmFileEntry) index.get(key);
					fileEntry.addOffset(offset);
				} else {
					files.add(fileEntry);
					index.put(key, fileEntry);
				}
			}
		}
		return files;
	}

	/**
	 * Create a new FileEntry.
	 * @see com.webcodepro.applecommander.storage.DirectoryEntry#createFile()
	 */
	public FileEntry createFile() throws DiskFullException {
		return null;
	}

	/**
	 * Identify if additional directories can be created.  CP/M doesn't
	 * support directories.
	 * @see com.webcodepro.applecommander.storage.DirectoryEntry#canCreateDirectories()
	 */
	public boolean canCreateDirectories() {
		return false;
	}

	/**
	 * Indicates if this disk image can create a file.
	 * @see com.webcodepro.applecommander.storage.DirectoryEntry#canCreateFile()
	 */
	public boolean canCreateFile() {
		return false;
	}
	
	/**
	 * Read a CP/M block (1K in size).
	 */
	public byte[] readCpmBlock(int block) {
		byte[] data = new byte[CPM_BLOCKSIZE];
		int track = computeTrack(block);
		int sector = computeSector(block);
		for (int i=0; i<PHYSICAL_SECTORS_PER_BLOCK; i++) {
			System.arraycopy(readSector(track, sectorSkew[sector+i]), 
				0, data, i*SECTOR_SIZE, SECTOR_SIZE);
		}
		return data;
	}
	
	/**
	 * Compute the physical track number.
	 */
	protected int computeTrack(int block) {
		return PHYSICAL_BLOCK_TRACK_START + (block / CPM_BLOCKS_PER_TRACK);
	}
	
	/**
	 * Compute the physical sector number.  The rest of the block
	 * follows in sequential order.
	 */
	protected int computeSector(int block) {
		return (block % CPM_BLOCKS_PER_TRACK) * PHYSICAL_SECTORS_PER_BLOCK;
	}
	
	/**
	 * Write a CP/M block.
	 */
	public void writeCpmBlock(int block, byte[] data) {
		int track = computeTrack(block);
		int sector = computeSector(block);
		byte[] sectorData = new byte[SECTOR_SIZE];
		for (int i=0; i<PHYSICAL_SECTORS_PER_BLOCK; i++) {
			System.arraycopy(data, i*SECTOR_SIZE, sectorData, 0, SECTOR_SIZE);
			writeSector(track, sectorSkew[sector+i], sectorData);
		}
	}

	/**
	 * Get the standard file column header information.
	 * This default implementation is intended only for standard mode.
	 */
	public List getFileColumnHeaders(int displayMode) {
		List list = new ArrayList();
		switch (displayMode) {
			case FILE_DISPLAY_NATIVE:
				list.add(new FileColumnHeader("Name", 8, FileColumnHeader.ALIGN_LEFT));
				list.add(new FileColumnHeader("Type", 3, FileColumnHeader.ALIGN_LEFT));
				break;
			case FILE_DISPLAY_DETAIL:
				list.add(new FileColumnHeader("Name", 8, FileColumnHeader.ALIGN_LEFT));
				list.add(new FileColumnHeader("Type", 3, FileColumnHeader.ALIGN_LEFT));
				list.add(new FileColumnHeader("Size (bytes)", 6, FileColumnHeader.ALIGN_RIGHT));
				list.add(new FileColumnHeader("User#", 4, FileColumnHeader.ALIGN_RIGHT));
				list.add(new FileColumnHeader("Deleted?", 7, FileColumnHeader.ALIGN_CENTER));
				list.add(new FileColumnHeader("Locked?", 6, FileColumnHeader.ALIGN_CENTER));
				break;
			default:	// FILE_DISPLAY_STANDARD
				list.addAll(super.getFileColumnHeaders(displayMode));
				break;
		}
		return list;
	}

	/**
	 * Indicates if this FormattedDisk supports a disk map.
	 */	
	public boolean supportsDiskMap() {
		return true;
	}
}