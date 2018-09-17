DROP TABLE PORTAL_CAT CASCADE;
DROP TABLE  PORTAL CASCADE;
 

-- CREATE PORTAL TABLE
 
CREATE TABLE "PORTAL"
   (    "PORTAL_KEY"   NUMBER NOT NULL ,             
        "URL_NAME"     VARCHAR2(10) NOT NULL, 
        "PORTAL_VERSION"       NUMBER,           
        "FULL_NAME"    VARCHAR2(500) NOT NULL,       
        "PAGE_NAME"    VARCHAR2(500) NOT NULL,
        "PAGE_TITLE"   VARCHAR2(500) NOT NULL,
        "PAGE_IMG_URL"   VARCHAR2(500),        
        "PAGE_CATEGORY_PAGE_DESC" VARCHAR2(4000),
        "PAGE_SUB_CATEGORY_DESC" VARCHAR2(4000),
        "PAGE_SUMMARY_DESC"  VARCHAR2(4000),
        "DATE_LAST_UPDATED"  DATE,    
        "PORTAL_STATUS"   VARCHAR2(10),
        "PORTAL_TYPE"  VARCHAR2(10) not NULL, 
        "TERM_NAME"   VARCHAR2(255),
        CONSTRAINT "PORTAL_PORTAL_KEY_PK" PRIMARY KEY ("PORTAL_KEY")
);
 
--CREATE UNIQUE CONSTRAINTS
 
ALTER TABLE PORTAL ADD (CONSTRAINT PORTAL_URL_NAME_UC UNIQUE (URL_NAME));
ALTER TABLE PORTAL ADD (CONSTRAINT PORTAL_PORTAL_VERSION_UC UNIQUE (PORTAL_VERSION));
 
-- CREATE COMMENTS
 
COMMENT ON TABLE PORTAL is 'Stores information about the Portals.';
COMMENT ON COLUMN PORTAL.PORTAL_KEY is 'Portal key/identifier, primary key.';
COMMENT ON COLUMN PORTAL.URL_NAME is 'Unique short name of the portal called on the URL.';
COMMENT ON COLUMN PORTAL.PORTAL_VERSION is 'Unique build/version name for a certain week''s data.';
COMMENT ON COLUMN PORTAL.FULL_NAME is 'Full name of the portal on the page.';
COMMENT ON COLUMN PORTAL.PAGE_NAME is 'Name of the portal on the page.';
COMMENT ON COLUMN PORTAL.PAGE_TITLE is 'Portal page title.';
COMMENT ON COLUMN PORTAL.PAGE_IMG_URL is 'Relative URL to a portal''s image.';
COMMENT ON COLUMN PORTAL.PAGE_CATEGORY_PAGE_DESC is 'Description words on the page for the categories.';
COMMENT ON COLUMN PORTAL.PAGE_SUB_CATEGORY_DESC is 'Description words on the page for the sub-categories.';
COMMENT ON COLUMN PORTAL.PAGE_SUMMARY_DESC is 'Summary description on the page.';
COMMENT ON COLUMN PORTAL.DATE_LAST_UPDATED is 'Date a portal''s data was last updated.';
COMMENT ON COLUMN PORTAL.PORTAL_STATUS is 'Status of a portal -- new,active,being updated.';
COMMENT ON COLUMN PORTAL.PORTAL_TYPE is 'Type of portal -- Standard or Advanced.';
COMMENT ON COLUMN PORTAL.TERM_NAME is 'TERM name.';
 
-- CREATE PORTAL_CAT TABLE
 
CREATE TABLE "PORTAL_CAT"
   (        "PORTAL_CAT_ID"  NUMBER NOT NULL,
            "PORTAL_KEY"  NUMBER, 
         "PORTAL_VERSION"    number,
            "CATEGORY_NAME"  VARCHAR2(500),
            "PARENT_CAT_ID"  NUMBER,
            "SUMMARY_TABLE_HTML" CLOB,
            "GVIEWER_XML_RAT"  CLOB,
            "GVIEWER_XML_MOUSE" CLOB,
            "GVIEWER_XML_HUMAN" CLOB,
            "GENE_INFO_HTML"  CLOB,
            "QTL_INFO_HTML"  CLOB,
            "STRAIN_INFO_HTML"  CLOB,
        CONSTRAINT PORTAL_CAT_PORTAL_CAT_ID_PK PRIMARY KEY (PORTAL_CAT_ID) 
)
/
 

--CREATE UNIQUE CONSTRAINT
ALTER TABLE PORTAL_CAT ADD (CONSTRAINT PORTAL_CAT_PORTAL_VERSION_UC UNIQUE (PORTAL_VERSION))
/
 
-- CREATE FOREIGN KEYS
 
ALTER TABLE PORTAL_CAT ADD CONSTRAINT PORTAL_CAT_PORTAL_PORTALKEY_FK FOREIGN KEY (PORTAL_KEY)
 REFERENCES PORTAL (PORTAL_KEY)
/
 
-- SELF-REFERENCING/RECURSIVE FK
 
ALTER TABLE PORTAL_CAT ADD CONSTRAINT PORTALCAT_PRNTCTIDPRTLCTID_RFK FOREIGN KEY (PARENT_CAT_ID)
 REFERENCES PORTAL_CAT (PORTAL_CAT_ID)
/
 

-- CREATE COMMENTS       
 
COMMENT ON TABLE PORTAL_CAT is 'Stores information about the portal categories.';
COMMENT ON COLUMN PORTAL_CAT.PORTAL_CAT_ID is 'Portal category identifier, primary key.';
COMMENT ON COLUMN PORTAL_CAT.PORTAL_KEY is 'Portal key, FK to PORTAL.PORTAL_KEY, indicates to which portal the category belongs.';
COMMENT ON COLUMN PORTAL_CAT.PORTAL_VERSION is 'Unique version/build name for a certain week''s data; enables keeping old data available.';
COMMENT ON COLUMN PORTAL_CAT.CATEGORY_NAME is 'Display name of a category.';
COMMENT ON COLUMN PORTAL_CAT.PARENT_CAT_ID is 'Parent category identifier, is NULL for top level categories, recursive FK to PORTAL_CAT.PORTAL_CAT_ID for sub-categories.';
COMMENT ON COLUMN PORTAL_CAT.SUMMARY_TABLE_HTML is 'Summary table HTML to pass into a  portal.';
COMMENT ON COLUMN PORTAL_CAT.GVIEWER_XML_RAT is 'GViewer data to pass into a portal for Rat.';
COMMENT ON COLUMN PORTAL_CAT.GVIEWER_XML_MOUSE is 'GViewer data to pass into a portal for Mouse.';
COMMENT ON COLUMN PORTAL_CAT.GVIEWER_XML_HUMAN is 'GViewer data to pass into a portal for Human.';
COMMENT ON COLUMN PORTAL_CAT.GENE_INFO_HTML is 'Gene data to pass into a portal.';
COMMENT ON COLUMN PORTAL_CAT.QTL_INFO_HTML is 'QTL data to pass into a portal.';
COMMENT ON COLUMN PORTAL_CAT.STRAIN_INFO_HTML is 'Strain data to pass into a portal.';
 
 
 
 
 
-- DATA Pre-*populated
 
insert into PORTAL values 
(1,'nuro',20060921, 'Neurological Disease Portal','disease','Rat Genome Database : Nerological Disease Portal',
 '/common/dportal/images/neurological.gif', '1. Choose a disease category','2. Choose a disease:',
'Data for all neurological diseases below', sysdate,'new','Standard',0,0,NULL)
/
 
 
 
drop SEQUENCE PORTAL_SEQ;
CREATE SEQUENCE  "PORTAL_SEQ"  NOCACHE;

drop SEQUENCE PORTAL_CAT_SEQ;
CREATE SEQUENCE  "PORTAL_CAT_SEQ"  NOCACHE;
 