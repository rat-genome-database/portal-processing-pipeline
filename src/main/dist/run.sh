# run portal processing pipeline
# first handle all disease portals, then all ontology portals, last portal objects
# list of portals processed is in file properties/AppConfigure.xml
#
# optional command line parameters:
#  -skipPortals
#  -skipOntologies
#  -skipObjects
#
. /etc/profile

APPNAME=PortalProcessing
APPDIR=/home/rgddata/pipelines/$APPNAME
SERVER=`hostname -s | tr '[a-z]' '[A-Z]'`
EMAIL_LIST=mtutaj@mcw.edu
if [ "$SERVER" = "REED" ]; then
  EMAIL_LIST=rgd.developers@mcw.edu
fi

cd $APPDIR
java -Dspring.config=$APPDIR/../properties/default_db.xml \
    -Dlog4j.configuration=file://$APPDIR/properties/log4j.properties \
    -jar lib/${APPNAME}.jar "$@" > run.log 2>&1

mailx -s "[$SERVER] PortalProcessing done" $EMAIL_LIST < $APPDIR/logs/status.log
