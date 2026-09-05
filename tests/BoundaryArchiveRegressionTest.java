import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Tiny, entirely synthetic shapefiles; no GIS dependency or licensed Stata. */
public final class BoundaryArchiveRegressionTest {
    private static int assertions;
    private static void check(boolean value, String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }
    private static double[][] square(double x, double y, double size) {
        return new double[][]{{x,y},{x,y+size},{x+size,y+size},{x+size,y},{x,y}};
    }
    private static byte[] polygon(double[][]... rings) {
        int count = 0;
        for (double[][] ring : rings) count += ring.length;
        ByteBuffer b = ByteBuffer.allocate(44 + 4*rings.length + 16*count).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(5); b.putDouble(0); b.putDouble(0); b.putDouble(180); b.putDouble(90);
        b.putInt(rings.length); b.putInt(count);
        int index=0; for (double[][] ring : rings) { b.putInt(index); index+=ring.length; }
        for (double[][] ring : rings) for (double[] point : ring) { b.putDouble(point[0]); b.putDouble(point[1]); }
        return b.array();
    }
    private static byte[] shapes() throws IOException {
        List<byte[]> shapes=Arrays.asList(polygon(square(0,0,1)), polygon(square(70,0,1)),
                new byte[4], polygon(square(79,6,1), square(79.4,6.4,.2)), polygon(square(81,8,1)));
        int bytes=100; for (byte[] shape : shapes) bytes+=8+shape.length;
        ByteBuffer b=ByteBuffer.allocate(bytes).order(ByteOrder.BIG_ENDIAN);
        b.putInt(9994); b.position(24); b.putInt(bytes/2); b.order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(1000); b.putInt(5); b.position(100);
        int number=1; for(byte[] shape:shapes) {
            b.order(ByteOrder.BIG_ENDIAN);b.putInt(number++);b.putInt(shape.length/2);b.put(shape);
        }
        return b.array();
    }
    private static byte[] attributes(Charset charset) {
        String[] fields={"NAM_0","NAM_1","NAM_2","ISO_A3"};
        int[] lengths={36,24,64,3};int rowLength=1;
        for(int length:lengths)rowLength+=length;
        int header=33+32*fields.length;
        ByteBuffer b=ByteBuffer.allocate(header+5*rowLength+1).order(ByteOrder.LITTLE_ENDIAN);
        b.put((byte)3);b.position(4);b.putInt(5);b.putShort((short)header);b.putShort((short)rowLength);
        int offset=1;
        for(int i=0;i<fields.length;i++){
            b.position(32+i*32);b.put(fields[i].getBytes(StandardCharsets.US_ASCII));
            b.position(32+i*32+11);b.put((byte)'C');b.putInt(offset);b.put((byte)lengths[i]);offset+=lengths[i];
        }
        b.position(header-1);b.put((byte)13);
        String[][] rows={{"Other","Elsewhere","Other","OTH"},{"Sri Lanka","Deleted","Deleted","LKA"},
                {"Sri Lanka","No geometry","Null record","LKA"},{"Sri Lanka","Western","Colombo","LKA"},
                {"Sri Lanka","Western","Gampáha < & >","LKA"}};
        for(int i=0;i<rows.length;i++){
            b.put((byte)(i==1?'*':' '));
            for(int j=0;j<fields.length;j++){
                byte[] value=rows[i][j].getBytes(charset);
                for(int k=0;k<lengths[j];k++) b.put(k<value.length?value[k]:(byte)' ');
            }
        }
        b.put((byte)26);return b.array();
    }
    private static void entry(ZipOutputStream z,String name,byte[] bytes) throws IOException {
        z.putNextEntry(new ZipEntry(name));z.write(bytes);z.closeEntry();
    }
    private static Path archive(Path dir,String codec,boolean projected,boolean metadataOnly) throws IOException {
        Path path=Files.createTempFile(dir,"boundary-", ".zip");
        try(ZipOutputStream z=new ZipOutputStream(Files.newOutputStream(path))) {
            for(String ext:Arrays.asList("shp","dbf","cpg","prj")) {
                entry(z,"__MACOSX/._WB_GAD_ADM2."+ext,new byte[]{0,5,22,7});
                entry(z,"nested/._WB_GAD_ADM2."+ext,new byte[]{0,5,22,7});
                entry(z,"nested/__MACOSX/resource."+ext,new byte[]{0,5,22,7});
            }
            if(!metadataOnly){
                String stem="nested/WB_GAD_ADM2";
                entry(z,stem+".SHP",shapes());
                entry(z,stem+".DBF",attributes("1252".equals(codec)?Charset.forName("windows-1252"):StandardCharsets.UTF_8));
                entry(z,stem+".CPG",codec.getBytes(StandardCharsets.UTF_8));
                entry(z,stem+".PRJ",(projected?"PROJCS[\"UTM\"]":"GEOGCS[\"GCS_WGS_1984\"]").getBytes(StandardCharsets.UTF_8));
            }
        }
        return path;
    }
    private static void expectFailure(Path archive,String message){
        try{BoundaryMap.load("Sri Lanka",archive.toString(),new ArrayList<String>());throw new AssertionError("Expected failure: "+message);}
        catch(IllegalArgumentException e){check(e.getMessage().contains(message),"Clear diagnostic: "+e.getMessage());}
    }
    public static void main(String[] args) throws Exception {
        Path dir=Files.createTempDirectory("surveye-boundary-test-");
        try {
            for(String codec:Arrays.asList("UTF-8-SIG","utf_8_sig","\uFEFFUTF-8","UTF8","65001","1252")){
                BoundaryMap.MapGeometry g=BoundaryMap.load("LKA",archive(dir,codec,false,false).toString(),new ArrayList<String>());
                check(g.admin2,"Admin-2 recognition");check(g.featureCount==2,"Ignore deleted and null geometries");
                check(g.featureLabels.size()==g.encodedFeatures.size(),"One label per rendered feature");
                check(g.featureLabels.get(0).equals("Colombo · Western"),"Do not shift names after null/deleted shapes");
                check(g.featureLabels.get(1).equals("Gampáha < & > · Western"),"Unicode and special characters retained");
                check(g.contains(79.1,6.1),"Outer interior");check(!g.contains(79.5,6.5),"Polygon hole remains outside");
                check(g.contains(79.4,6.5),"Hole edge counts as boundary");check(g.contains(81.5,8.5),"Disjoint island");
                check(!g.contains(80.5,7.5),"Between polygons is outside");check(!g.contains(Double.NaN,7),"Invalid point rejected");
                check(!g.contains(70.5,.5),"Deleted DBF record excluded");
            }
            expectFailure(archive(dir,"made-up-codec",false,false),"Unsupported DBF code page");
            expectFailure(archive(dir,"UTF-8-SIG",true,false),"Projected shapefiles are not supported");
            expectFailure(archive(dir,"UTF-8-SIG",false,true),"matching .shp and .dbf");
            BoundaryMap.MapGeometry bundled=BoundaryMap.load("Sri Lanka",null,new ArrayList<String>());
            check(bundled.featureLabels.size()==bundled.encodedFeatures.size(),"Bundled outline label alignment");
            check(!bundled.admin2,"Bundled outline is not misrepresented as Admin-2");
            System.out.println("PASS boundary archive regressions ("+assertions+" assertions): Mac metadata, UTF-8-SIG, names, null/deleted rows, holes, islands, errors");
        } finally {
            try(java.util.stream.Stream<Path> paths=Files.list(dir)){for(Path p:(Iterable<Path>)paths::iterator)Files.deleteIfExists(p);}
            Files.deleteIfExists(dir);
        }
    }
}
