       IDENTIFICATION DIVISION.
       PROGRAM-ID. ADDITION.
       DATA DIVISION.
       WORKING-STORAGE SECTION.
       01 A PIC 9 VALUE 2.
       01 B PIC 9 VALUE 3.
       01 C PIC 9.

       PROCEDURE DIVISION.
           ADD A TO B GIVING C
           DISPLAY "A + B = " C
           GOBACK.
