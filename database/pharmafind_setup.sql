/* =====================================================================
   PharmaFind - official database setup script (SQL Server)

   HOW TO USE
   1. Open the PharmaFind database in SQL Server Management Studio
      (or the Somee control panel's SQL console).
   2. Run this whole file once. It is safe to run again at any time:
      every statement checks first, nothing is dropped, and no data
      is ever changed or deleted.

   WHAT IT DOES
   - Part 1 creates any table that does not exist yet.
   - Part 2 adds any column an older copy of the database is missing.
   The app also repairs missing columns by itself when it runs, but this
   file is the single place that describes what the database should be.

   Tables:  Customers, Pharmacies, Inquiries, PharmacyReplies, Messages
   (the "sysdiagrams" table in the live database is created by SQL Server
   for diagrams and is not part of PharmaFind.)
   ===================================================================== */


/* ---------------------------------------------------------------------
   PART 1 - TABLES
   Order matters: Inquiries and PharmacyReplies point at the first two.
   --------------------------------------------------------------------- */

IF OBJECT_ID(N'dbo.Customers', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.Customers (
        CustomerID     INT IDENTITY(1,1) PRIMARY KEY,
        FirstName      NVARCHAR(100)  NULL,
        Surname        NVARCHAR(100)  NULL,
        MiddleInitial  NVARCHAR(5)    NULL,
        City           NVARCHAR(100)  NULL,
        Barangay       NVARCHAR(100)  NULL,
        District       NVARCHAR(100)  NULL,
        Age            INT            NULL,
        BirthDate      DATE           NULL,
        Email          NVARCHAR(255)  NULL UNIQUE,
        Contact        NVARCHAR(20)   NULL,
        Username       NVARCHAR(50)   NULL UNIQUE,
        Password       NVARCHAR(255)  NULL,
        ProfilePicture NVARCHAR(255)  NULL,
        Bio            NVARCHAR(MAX)  NULL,
        Latitude       FLOAT          NULL,
        Longitude      FLOAT          NULL,
        CreatedAt      DATETIME       NULL DEFAULT GETDATE()
    );
END
GO

IF OBJECT_ID(N'dbo.Pharmacies', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.Pharmacies (
        PharmacyID        INT IDENTITY(1,1) PRIMARY KEY,
        PharmacyName      NVARCHAR(255)  NOT NULL,
        City              NVARCHAR(100)  NOT NULL,
        Barangay          NVARCHAR(100)  NOT NULL,
        District          NVARCHAR(100)  NULL,
        StreetAddress     NVARCHAR(255)  NULL,
        Email             NVARCHAR(255)  NOT NULL UNIQUE,
        Contact           NVARCHAR(20)   NOT NULL,
        Username          NVARCHAR(50)   NOT NULL UNIQUE,
        Password          NVARCHAR(255)  NOT NULL,
        Status            NVARCHAR(20)   NULL DEFAULT 'pending',   -- 'pending', 'approved', 'rejected'
        PermitDocumentUrl NVARCHAR(255)  NULL,
        ProfilePicture    NVARCHAR(255)  NULL,
        CoverPhoto        NVARCHAR(255)  NULL,
        OperatingHours    NVARCHAR(100)  NULL DEFAULT '8:00 AM - 8:00 PM',
        Bio               NVARCHAR(MAX)  NULL,
        Latitude          DECIMAL(10, 8) NULL,                     -- store pin for Google Maps
        Longitude         DECIMAL(11, 8) NULL,
        CreatedAt         DATETIME       NULL DEFAULT GETDATE()
    );
END
GO

IF OBJECT_ID(N'dbo.Inquiries', N'U') IS NULL
BEGIN
    -- "Inquiries" are the medicine requests customers post.
    CREATE TABLE dbo.Inquiries (
        InquiryID          INT IDENTITY(1,1) PRIMARY KEY,
        CustomerID         INT            NOT NULL REFERENCES dbo.Customers (CustomerID),
        CustomerName       NVARCHAR(100)  NULL,
        MedicineName       NVARCHAR(150)  NOT NULL,
        Description        NVARCHAR(MAX)  NULL,
        ImageUrl           NVARCHAR(MAX)  NULL,                    -- photo stored as Base64 text, so it must be MAX
        City               NVARCHAR(100)  NOT NULL,
        Barangay           NVARCHAR(100)  NOT NULL,
        District           NVARCHAR(100)  NULL,
        Status             NVARCHAR(20)   NULL DEFAULT 'open',
        AcceptedPharmacyID INT            NULL REFERENCES dbo.Pharmacies (PharmacyID),
        CreatedAt          DATETIME       NULL DEFAULT GETDATE()
    );
END
GO

IF OBJECT_ID(N'dbo.PharmacyReplies', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.PharmacyReplies (
        ReplyID            INT IDENTITY(1,1) PRIMARY KEY,
        InquiryID          INT            NOT NULL REFERENCES dbo.Inquiries (InquiryID),
        PharmacyID         INT            NOT NULL REFERENCES dbo.Pharmacies (PharmacyID),
        PharmacyName       NVARCHAR(100)  NULL,
        AvailabilityStatus NVARCHAR(50)   NULL,                    -- 'In Stock', 'Available', 'Limited Stock'
        Price              NVARCHAR(50)   NULL,
        Note               NVARCHAR(MAX)  NULL,
        IsAvailable        BIT            NULL DEFAULT 1,          -- older columns, no longer written by the app
        PriceInfo          NVARCHAR(100)  NULL,
        CreatedAt          DATETIME       NULL DEFAULT GETDATE()
    );
END
GO

IF OBJECT_ID(N'dbo.Messages', N'U') IS NULL
BEGIN
    -- Direct messages between a customer and a pharmacy about one request.
    CREATE TABLE dbo.Messages (
        MessageID   INT IDENTITY(1,1) PRIMARY KEY,
        InquiryID   INT            NOT NULL,
        SenderID    INT            NOT NULL,
        SenderRole  NVARCHAR(20)   NOT NULL,                       -- 'customer' or 'pharmacy'
        SenderName  NVARCHAR(100)  NOT NULL,
        ReceiverID  INT            NOT NULL,
        MessageText NVARCHAR(MAX)  NOT NULL,
        CreatedAt   DATETIME       NULL DEFAULT GETDATE()
    );
END
GO


/* ---------------------------------------------------------------------
   PART 2 - COLUMNS ADDED LATER
   Only matters for a database created from an older version of the
   project. A new database from Part 1 already has all of these.
   --------------------------------------------------------------------- */

-- Customers
IF COL_LENGTH('dbo.Customers', 'ProfilePicture') IS NULL ALTER TABLE dbo.Customers ADD ProfilePicture NVARCHAR(255) NULL;
IF COL_LENGTH('dbo.Customers', 'Bio')            IS NULL ALTER TABLE dbo.Customers ADD Bio NVARCHAR(MAX) NULL;
IF COL_LENGTH('dbo.Customers', 'District')       IS NULL ALTER TABLE dbo.Customers ADD District NVARCHAR(100) NULL;
IF COL_LENGTH('dbo.Customers', 'Latitude')       IS NULL ALTER TABLE dbo.Customers ADD Latitude FLOAT NULL;
IF COL_LENGTH('dbo.Customers', 'Longitude')      IS NULL ALTER TABLE dbo.Customers ADD Longitude FLOAT NULL;
IF COL_LENGTH('dbo.Customers', 'CreatedAt')      IS NULL ALTER TABLE dbo.Customers ADD CreatedAt DATETIME NULL DEFAULT GETDATE();
GO

-- Pharmacies
IF COL_LENGTH('dbo.Pharmacies', 'District')          IS NULL ALTER TABLE dbo.Pharmacies ADD District NVARCHAR(100) NULL;
IF COL_LENGTH('dbo.Pharmacies', 'StreetAddress')     IS NULL ALTER TABLE dbo.Pharmacies ADD StreetAddress NVARCHAR(255) NULL;
IF COL_LENGTH('dbo.Pharmacies', 'Status')            IS NULL ALTER TABLE dbo.Pharmacies ADD Status NVARCHAR(20) NULL DEFAULT 'pending';
IF COL_LENGTH('dbo.Pharmacies', 'PermitDocumentUrl') IS NULL ALTER TABLE dbo.Pharmacies ADD PermitDocumentUrl NVARCHAR(255) NULL;
IF COL_LENGTH('dbo.Pharmacies', 'ProfilePicture')    IS NULL ALTER TABLE dbo.Pharmacies ADD ProfilePicture NVARCHAR(255) NULL;
IF COL_LENGTH('dbo.Pharmacies', 'CoverPhoto')        IS NULL ALTER TABLE dbo.Pharmacies ADD CoverPhoto NVARCHAR(255) NULL;
IF COL_LENGTH('dbo.Pharmacies', 'OperatingHours')    IS NULL ALTER TABLE dbo.Pharmacies ADD OperatingHours NVARCHAR(100) NULL DEFAULT '8:00 AM - 8:00 PM';
IF COL_LENGTH('dbo.Pharmacies', 'Bio')               IS NULL ALTER TABLE dbo.Pharmacies ADD Bio NVARCHAR(MAX) NULL;   -- the pharmacy profile screens read this
IF COL_LENGTH('dbo.Pharmacies', 'Latitude')          IS NULL ALTER TABLE dbo.Pharmacies ADD Latitude DECIMAL(10, 8) NULL;
IF COL_LENGTH('dbo.Pharmacies', 'Longitude')         IS NULL ALTER TABLE dbo.Pharmacies ADD Longitude DECIMAL(11, 8) NULL;
GO

-- Inquiries
IF COL_LENGTH('dbo.Inquiries', 'CustomerName')       IS NULL ALTER TABLE dbo.Inquiries ADD CustomerName NVARCHAR(100) NULL;
IF COL_LENGTH('dbo.Inquiries', 'Description')        IS NULL ALTER TABLE dbo.Inquiries ADD Description NVARCHAR(MAX) NULL;
IF COL_LENGTH('dbo.Inquiries', 'ImageUrl')           IS NULL ALTER TABLE dbo.Inquiries ADD ImageUrl NVARCHAR(MAX) NULL;
IF COL_LENGTH('dbo.Inquiries', 'District')           IS NULL ALTER TABLE dbo.Inquiries ADD District NVARCHAR(100) NULL;
IF COL_LENGTH('dbo.Inquiries', 'AcceptedPharmacyID') IS NULL ALTER TABLE dbo.Inquiries ADD AcceptedPharmacyID INT NULL;
-- The first setup script made ImageUrl NVARCHAR(255), far too small for a Base64 photo.
IF COL_LENGTH('dbo.Inquiries', 'ImageUrl') <> -1     ALTER TABLE dbo.Inquiries ALTER COLUMN ImageUrl NVARCHAR(MAX) NULL;
GO

-- PharmacyReplies
IF COL_LENGTH('dbo.PharmacyReplies', 'PharmacyName')       IS NULL ALTER TABLE dbo.PharmacyReplies ADD PharmacyName NVARCHAR(100) NULL;
IF COL_LENGTH('dbo.PharmacyReplies', 'AvailabilityStatus') IS NULL ALTER TABLE dbo.PharmacyReplies ADD AvailabilityStatus NVARCHAR(50) NULL;
IF COL_LENGTH('dbo.PharmacyReplies', 'Price')              IS NULL ALTER TABLE dbo.PharmacyReplies ADD Price NVARCHAR(50) NULL;
GO
