package ai.susi.server.api.cms;

import ai.susi.DAO;
import ai.susi.json.JsonObjectWithDefault;
import ai.susi.mind.SusiSkill;
import ai.susi.server.*;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.json.JSONObject;

import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;


/**
 * Created by chetankaushik on 06/06/17.
 * This Service deletes a skill as per given query.
 * http://localhost:4000/cms/deleteSkill.txt?model=general&group=Knowledge&language=en&skill=whois
 * When someone deletes a skill then it will move a folder delete_skills_dir.
 * When a file is moved to the delete_skills_dir its last modified date is changed to the current date.
 * Then in the caretaker there is a function which checks for files which are older than 30 days by checking the last modified date.
 * If there is any file which is older than 30 days then it deletes them.
 */

public class DeleteSkillService extends AbstractAPIHandler implements APIHandler {

    private static final long serialVersionUID = -1755374387315534691L;

    @Override
    public UserRole getMinimalUserRole() {
        return UserRole.ADMIN;
    }

    @Override
    public JSONObject getDefaultPermissions(UserRole baseUserRole) {
        return null;
    }

    @Override
    public String getAPIPath() {
        return "/cms/deleteSkill.json";
    }

    @Override
    public ServiceResponse serviceImpl(Query call, HttpServletResponse response, Authorization rights, final JsonObjectWithDefault permissions) {

        String model_name = call.get("model", "general");
        File model = new File(DAO.model_watch_dir, model_name);
        String group_name = call.get("group", "Knowledge");
        File group = new File(model, group_name);
        String language_name = call.get("language", "en");
        File language = new File(group, language_name);
        String skill_name = call.get("skill", "whois");
        File skill = SusiSkill.getSkillFileInLanguage(language, skill_name, false);
        JSONObject json = new JSONObject(true);
        json.put("accepted", false);
        if(!DAO.deleted_skill_dir.exists()){
            DAO.deleted_skill_dir.mkdirs();
        }
        String path = skill.getPath();
        path = path.replace(DAO.model_watch_dir.getPath(),"");

        if (skill.exists()) {
            File file = new File(DAO.deleted_skill_dir.getPath()+path);
            file.getParentFile().mkdirs();
            if(skill.renameTo(file)){
                Boolean changed =  new File(DAO.deleted_skill_dir.getPath()+path).setLastModified(System.currentTimeMillis());
                System.out.print(changed);
                System.out.println("Skill moved successfully!");
            }else{
                System.out.println("Skill failed to move!");
            }

            json.put("message","Deleted "+ skill_name);

            //Add to git
            try (Git git = DAO.getGit()) {
                git.add()
                        .setUpdate(true)
                        .addFilepattern(".")
                        .call();
                // and then commit the changes
                DAO.pushCommit(git, "Deleted " + skill_name, rights.getIdentity().isEmail() ? rights.getIdentity().getName() : "anonymous@");
                json.put("accepted", true);
                json.put("message", "Deleted " + skill_name);
            } catch (IOException | GitAPIException e) {
                e.printStackTrace();
            }
        } else {
            json.put("message", "Cannot find '" + skill + "' ('" + skill.getAbsolutePath() + "')");
        }
        return new ServiceResponse(json);
    }

}
