***********************************************************                                                        ** APPLECOMMANDER boot code.                              ** Copyright (c) 2002, Rob Greene                         **                                                        ** This code is published under the GPL license.  See the ** AppleCommander site for license information.           **                                                        *********************************************************** ORG $800XEND = $2CADDR = $26LINENO = $25KEYBOARD = $C000TEXT = $FB2FHOME = $FC58GR = $FB40COLOR = $F864HLIN = $F819PRINT = $FDEDREBOOT = $FAA6CALCADDR = $F847DELAY = $FCA8XOFFSET = 14YOFFSET = 13 DFB 1 ; used by boot rom JSR TEXT JSR HOME JSR GR** Draw the AppleCommander logo (well, sorta)* LDX #DATA2-DATA1:LOGO LDA DATA1-1,X LSR LSR LSR LSR JSR COLOR LDA DATA2-1,X LSR LSR LSR LSR STA XEND LDA DATA1-1,X AND #$F TAY LDA DATA2-1,X AND #$F CLC ADC #YOFFSET JSR HLIN DEX BNE :LOGO** Display AppleCommander text*:TEXT LDA MESSAGE,X BEQ :WAIT JSR PRINT INX BNE :TEXT** Check for a keypress*:WAIT LDA KEYBOARD BPL :SETUP JMP REBOOT** Rotate the screen (isn't that retro!)*:SETUP LDA #19 STA LINENO:ROTATE JSR CALCADDR LDY #0 LDA (ADDR),Y PHA:SHIFT INY LDA (ADDR),Y DEY STA (ADDR),Y INY CPY #39 BNE :SHIFT PLA STA (ADDR),Y DEC LINENO LDA LINENO BPL :ROTATE** Introduce a pause between rotations*:KEYLOOP LDA #$08 JSR DELAY DEX BNE :KEYLOOP BEQ :WAIT* DATA1 consists of a color nybble and a x1 (start) position.DATA1 HEX C8C7C6C3C8C2 ; green HEX D1D1 ; yellow HEX 9090 ; orange HEX 1010 ; red HEX 3131 ; purple HEX 626368 ; blue* DATA2 consists of x2 (end) and y position.DATA2 HEX 90817253B3C4 HEX D5D6 HEX C7B8 HEX B9CA HEX DBDC HEX CD5EBEMESSAGE ASC "THIS DISK CREATED WITH APPLECOMMANDER"8D ASC "GET IT AT APPLECOMMANDER.SF.NET"8D ASC " "8D ASC "INSERT ANOTHER DISK AND PRESS ANY KEY"00